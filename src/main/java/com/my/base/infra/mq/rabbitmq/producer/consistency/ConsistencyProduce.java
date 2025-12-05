package com.my.base.infra.mq.rabbitmq.producer.consistency;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.my.base.infra.mq.rabbitmq.constants.RabbitEnum;
import com.my.base.infra.mq.rabbitmq.constants.RabbitExchangeTypeEnum;
import com.my.base.infra.mq.rabbitmq.consumer.ConsumerContainerFactory;
import com.my.base.infra.mq.rabbitmq.consumer.consistency.MsgLogService;
import com.my.base.infra.mq.rabbitmq.domain.MsgDto;
import com.my.base.infra.mq.rabbitmq.domain.MQSendParams;
import com.my.base.infra.mq.rabbitmq.helper.MsgHelper;
import com.my.base.infra.mq.rabbitmq.retry.CustomRetryListener;
import com.my.base.config.property.MQProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * RabbitConfig
 * </p>
 */
@Slf4j
@Service
public class ConsistencyProduce {

    /**
     * MQ链接工厂
     */
    private final ConnectionFactory connectionFactory;

    /**
     * MQ操作管理器
     */
    private final AmqpAdmin amqpAdmin;

    /**
     * 消息发送模板
     */
    private final RabbitTemplate rabbitTemplate;

    public ConsistencyProduce(ConnectionFactory connectionFactory, AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate) {
        this.connectionFactory = connectionFactory;
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送消息
     */
    public boolean sendQueue(MQSendParams sendParams) {
        String msgId = UUID.randomUUID().toString().replaceAll("-", "");
        sendParams.setMsgId(msgId);

        MQProperties mqProperties = new MQProperties();
        mqProperties.setRoutingKey(sendParams.getRoutingKey());
        // 构建队列参数
        MQProperties.Queue queue = new MQProperties.Queue();
        queue.setName(sendParams.getQueue());
        queue.setDurable(sendParams.isDurable());
        queue.setExclusive(sendParams.isExclusive());
        queue.setAutoDelete(sendParams.isAutoDelete());
        queue.setDeadLetterExchange(sendParams.getDeadLetterExchange());
        queue.setDeadLetterExchange(sendParams.getDeadLetterExchange());
        queue.setArguments(sendParams.getArguments());
        mqProperties.setQueue(queue);
        // 构建交换机参数
        MQProperties.Exchange exchange = new MQProperties.Exchange();
        exchange.setName(sendParams.getExchange());
        exchange.setDurable(sendParams.isDurable());
        exchange.setAutoDelete(sendParams.isAutoDelete());
        mqProperties.setExchange(exchange);
        // 合并配置
        merge(mqProperties);

        MsgLogService msgLogService = SpringUtil.getBean(MsgLogService.class);
        // 1.存储要消费的数据
        msgLogService.save(exchange.getName(),
                mqProperties.getRoutingKey(),
                queue.getName(), msgId, sendParams.getContent());

        // 2.发送消息到mq服务器中（附带消息ID）
        CorrelationData correlationData = new CorrelationData(msgId);
        rabbitTemplate.convertAndSend(exchange.getName(), mqProperties.getRoutingKey(),
                MsgHelper.objToMsg(new MsgDto(sendParams.getContent(), msgId)), correlationData);
        return true;
    }

    /**
     * 合并配置
     * @param properties
     */
    public void merge (MQProperties properties) {
        Queue queue = genQueue(properties);
        Exchange exchange = genQueueExchange(properties);
        queueBindExchange(queue, exchange, properties);
        if (StringUtils.isNotBlank(properties.getConsumer())) {
            bindConsumer(queue, exchange, properties);
        }
    }


    /**
     * 绑定消费者
     *
     * @param queue
     * @param exchange
     * @param properties
     */
    private void bindConsumer(Queue queue, Exchange exchange, MQProperties properties) {
        CustomRetryListener customRetryListener = null;
        try {
            customRetryListener = SpringUtil.getBean(properties.getRetry());
        } catch (Exception e) {
            log.debug("无法在容器中找到该重试类[{}]，若需要重试则需要做具体实现", properties.getRetry());
        }
        try {
            ConsumerContainerFactory factory = ConsumerContainerFactory.builder()
                    .connectionFactory(connectionFactory)
                    .queue(queue)
                    .exchange(exchange)
                    .consumer(SpringUtil.getBean(properties.getConsumer()))
                    .retryListener(customRetryListener)
                    .autoAck(properties.getAutoAck())
                    .amqpAdmin(amqpAdmin).build();
            SimpleMessageListenerContainer container = factory.getObject();
            if (Objects.nonNull(container)) {
                container.start();
            }
            log.debug("绑定消费者: {}", properties.getConsumer());
        } catch (Exception e) {
            log.warn("无法在容器中找到该消费者[{}]，若需要此消费者则需要做具体实现", properties.getConsumer());
        }
    }

    /**
     * 队列绑定交换机
     *
     * @param queue
     * @param exchange
     * @param properties
     */
    private void queueBindExchange(Queue queue, Exchange exchange, MQProperties properties) {
        log.debug("初始化交换机: {}", properties.getExchange().getName());
        String queueName = properties.getQueue().getName();
        String exchangeName = properties.getExchange().getName();
        properties.setRoutingKey(StrUtil.format(RabbitEnum.ROUTER_KEY.getValue(), properties.getRoutingKey()));
        String routingKey = properties.getRoutingKey();
        Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareBinding(binding);
        log.debug("队列绑定交换机: 队列: {}, 交换机: {}", queueName, exchangeName);
    }

    /**
     * 创建交换机
     *
     * @param properties
     * @return
     */
    private Exchange genQueueExchange(MQProperties properties) {
        MQProperties.Exchange exchange = properties.getExchange();
        RabbitExchangeTypeEnum exchangeType = exchange.getType();
        exchange.setName(StrUtil.format(RabbitEnum.EXCHANGE.getValue(), exchange.getName()));
        String exchangeName = exchange.getName();
        Boolean isDurable = exchange.isDurable();
        Boolean isAutoDelete = exchange.isAutoDelete();
        Map<String, Object> arguments = exchange.getArguments();
        return getExchangeByType(exchangeType, exchangeName, isDurable, isAutoDelete, arguments);
    }

    /**
     * 根据类型生成交换机
     *
     * @param exchangeType
     * @param exchangeName
     * @param isDurable
     * @param isAutoDelete
     * @param arguments
     * @return
     */
    private Exchange getExchangeByType(RabbitExchangeTypeEnum exchangeType, String exchangeName, Boolean isDurable, Boolean isAutoDelete, Map<String, Object> arguments) {
        AbstractExchange exchange = null;
        switch (exchangeType) {
            // 直连交换机
            case DIRECT:
                exchange = new DirectExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            // 主题交换机
            case TOPIC:
                exchange = new TopicExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            //扇形交换机
            case FANOUT:
                exchange = new FanoutExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            // 头交换机
            case HEADERS:
                exchange = new HeadersExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            default:
                log.warn("未匹配到交换机类型");
                break;
        }
        return exchange;
    }

    /**
     * 创建队列
     *
     * @param properties
     * @return
     */
    private Queue genQueue(MQProperties properties) {
        MQProperties.Queue queue = properties.getQueue();
        queue.setName(StrUtil.format(RabbitEnum.QUEUE.getValue(), queue.getName()));
        log.debug("初始化队列: {}", queue.getName());
        Map<String, Object> arguments = queue.getArguments();
        if (MapUtil.isEmpty(arguments)) {
            arguments = new HashMap<>();
        }

        // 转换ttl的类型为long
        if (arguments.containsKey("x-message-ttl")) {
            arguments.put("x-message-ttl", Convert.toLong(arguments.get("x-message-ttl")));
        }

        // 绑定死信队列
        String deadLetterExchange = queue.getDeadLetterExchange();
        String deadLetterRoutingKey = queue.getDeadLetterRoutingKey();
        if (StrUtil.isNotBlank(deadLetterExchange) && StrUtil.isNotBlank(deadLetterRoutingKey)) {
            deadLetterExchange = StrUtil.format(RabbitEnum.EXCHANGE.getValue(), deadLetterExchange);
            deadLetterRoutingKey = StrUtil.format(RabbitEnum.ROUTER_KEY.getValue(), deadLetterRoutingKey);
            arguments.put("x-dead-letter-exchange", deadLetterExchange);
            arguments.put("x-dead-letter-routing-key", deadLetterRoutingKey);
            log.debug("绑定死信队列: 交换机: {}, 路由: {}", deadLetterExchange, deadLetterRoutingKey);
        }
        return new Queue(queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete(), arguments);
    }
}
