package com.my.base.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.my.base.infra.mq.rabbitmq.consumer.ConsumerContainerFactory;
import com.my.base.infra.mq.rabbitmq.constants.RabbitEnum;
import com.my.base.infra.mq.rabbitmq.constants.RabbitExchangeTypeEnum;
import com.my.base.infra.mq.rabbitmq.producer.customize.AbsProducerService;
import com.my.base.infra.mq.rabbitmq.retry.CustomRetryListener;
import com.my.base.features.logging.MQFailLogStorage;
import com.my.base.config.property.MQProperties;
import com.my.base.config.property.RabbitModuleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 优雅封装RabbitMQ 实现动态队列、动态生产者，动态消费者绑定
 * RabbitMQ 全局配置
 */
@Slf4j
@Configuration
public class RabbitMqConfig implements SmartInitializingSingleton {

    /**
     * MQ链接工厂
     */
    private final ConnectionFactory connectionFactory;

    /**
     * MQ操作管理器
     */
    private final AmqpAdmin amqpAdmin;

    /**
     * YML配置
     */
    private final RabbitModuleProperties rabbitModuleProperties;
    /**
     * NACOS配置中心
     */
    private final ConfigService configService;

    @Value("${project.name}")
    private static String dataId = "base-starter";


    @Autowired
    public RabbitMqConfig(AmqpAdmin amqpAdmin, RabbitModuleProperties rabbitModuleProperties, ConnectionFactory connectionFactory, ConfigService configService) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitModuleProperties = rabbitModuleProperties;
        this.connectionFactory = connectionFactory;
        this.configService = configService;
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 设置消息转换器为json格式
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());

        // 消息是否成功发送到Exchange
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到Exchange失败, {}, cause: {}", correlationData, cause);
            }
        });

        // 触发setReturnCallback回调必须设置mandatory=true, 否则Exchange没有找到Queue就会丢弃掉消息, 而不会触发回调
        rabbitTemplate.setMandatory(true);

        // 消息是否从Exchange路由到Queue, 注意: 这是一个失败回调, 只有消息从Exchange路由到Queue失败才会回调这个方法
        rabbitTemplate.setReturnsCallback(returned -> {
            //TODO 可以记录发送失败的消息到msg_log表中
            MQFailLogStorage mqFailLogStorage = SpringUtil.getBean(MQFailLogStorage.class);
            mqFailLogStorage.save(returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText(), returned.getMessage().toString());
            log.error("消息从Exchange路由到Queue失败: exchange: {}, route: {}, replyCode: {}, replyText: {}, message: {}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText(), returned.getMessage());
        });

        return rabbitTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {

        try {
            // 监听配置中心
            configService.addListener(dataId, "DEFAULT_GROUP", new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    register();
                }

                @Override
                public Executor getExecutor() {
                    return null;
                }
            });
            // 启动时加载配置
            // register();
        } catch (NacosException e) {
            log.error("初始化MQ配置失败 {}", e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private void register() {
        StopWatch stopWatch = StopWatch.create("MQ");
        stopWatch.start();
        log.debug("初始化MQ配置");
        List<MQProperties> propertiesList = rabbitModuleProperties.getModules();
        if (CollUtil.isEmpty(propertiesList)) {
            log.warn("未配置MQ");
            return;
        }
        for (MQProperties properties : propertiesList) {
            try {
                Queue queue = genQueue(properties);
                Exchange exchange = genQueueExchange(properties);
                queueBindExchange(queue, exchange, properties);
                bindProducer(properties);
                bindConsumer(queue, exchange, properties);
            } catch (Exception e) {
                log.error("rabbitMQ 生产者消费者绑定初始化失败:", e);
            }
        }
        stopWatch.stop();
        log.info("初始化MQ配置成功耗时: {}ms", stopWatch.getTotal(TimeUnit.MILLISECONDS));
    }

    /**
     * 绑定生产者
     *
     * @param properties
     */
    private void bindProducer(MQProperties properties) {
        try {
            if (StrUtil.isBlank(properties.getProducer())) {
                return;
            }
            AbsProducerService<?> producerService = SpringUtil.getBean(properties.getProducer());
            producerService.setExchange(properties.getExchange().getName());
            producerService.setRoutingKey(properties.getRoutingKey());
            log.debug("绑定生产者: {}", properties.getProducer());
        } catch (Exception e) {
            log.warn("无法在容器中找到该生产者[{}]，若需要此生产者则需要做具体实现", properties.getConsumer());
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
