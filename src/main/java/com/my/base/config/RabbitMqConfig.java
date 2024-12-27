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
import com.my.base.common.rabbitmq.comsumer.ConsumerContainerFactory;
import com.my.base.common.rabbitmq.constants.RabbitEnum;
import com.my.base.common.rabbitmq.constants.RabbitExchangeTypeEnum;
import com.my.base.common.rabbitmq.product.AbsProducerService;
import com.my.base.common.rabbitmq.retry.CustomRetryListener;
import com.my.base.config.property.ModuleProperties;
import com.my.base.config.property.RabbitModuleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
            register();
        } catch (NacosException e) {
            log.error("初始化MQ配置失败 {}", e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private void register() {
        StopWatch stopWatch = StopWatch.create("MQ");
        stopWatch.start();
        log.debug("初始化MQ配置");
        List<ModuleProperties> modules = rabbitModuleProperties.getModules();
        if (CollUtil.isEmpty(modules)) {
            log.warn("未配置MQ");
            return;
        }
        for (ModuleProperties module : modules) {
            try {
                Queue queue = genQueue(module);
                Exchange exchange = genQueueExchange(module);
                queueBindExchange(queue, exchange, module);
                bindProducer(module);
                bindConsumer(queue, exchange, module);
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
     * @param module
     */
    private void bindProducer(ModuleProperties module) {
        try {
            AbsProducerService<?> producerService = SpringUtil.getBean(module.getProducer());
            producerService.setExchange(module.getExchange().getName());
            producerService.setRoutingKey(module.getRoutingKey());
            log.debug("绑定生产者: {}", module.getProducer());
        } catch (Exception e) {
            log.warn("无法在容器中找到该生产者[{}]，若需要此生产者则需要做具体实现", module.getConsumer());
        }
    }

    /**
     * 绑定消费者
     *
     * @param queue
     * @param exchange
     * @param module
     */
    private void bindConsumer(Queue queue, Exchange exchange, ModuleProperties module) {
        CustomRetryListener customRetryListener = null;
        try {
            customRetryListener = SpringUtil.getBean(module.getRetry());
        } catch (Exception e) {
            log.debug("无法在容器中找到该重试类[{}]，若需要重试则需要做具体实现", module.getRetry());
        }
        try {
            ConsumerContainerFactory factory = ConsumerContainerFactory.builder()
                    .connectionFactory(connectionFactory)
                    .queue(queue)
                    .exchange(exchange)
                    .consumer(SpringUtil.getBean(module.getConsumer()))
                    .retryListener(customRetryListener)
                    .autoAck(module.getAutoAck())
                    .amqpAdmin(amqpAdmin).build();
            SimpleMessageListenerContainer container = factory.getObject();
            if (Objects.nonNull(container)) {
                container.start();
            }
            log.debug("绑定消费者: {}", module.getConsumer());
        } catch (Exception e) {
            log.warn("无法在容器中找到该消费者[{}]，若需要此消费者则需要做具体实现", module.getConsumer());
        }
    }

    /**
     * 队列绑定交换机
     *
     * @param queue
     * @param exchange
     * @param module
     */
    private void queueBindExchange(Queue queue, Exchange exchange, ModuleProperties module) {
        log.debug("初始化交换机: {}", module.getExchange().getName());
        String queueName = module.getQueue().getName();
        String exchangeName = module.getExchange().getName();
        module.setRoutingKey(StrUtil.format(RabbitEnum.ROUTER_KEY.getValue(), module.getRoutingKey()));
        String routingKey = module.getRoutingKey();
        Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareBinding(binding);
        log.debug("队列绑定交换机: 队列: {}, 交换机: {}", queueName, exchangeName);
    }

    /**
     * 创建交换机
     *
     * @param module
     * @return
     */
    private Exchange genQueueExchange(ModuleProperties module) {
        ModuleProperties.Exchange exchange = module.getExchange();
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
     * @param module
     * @return
     */
    private Queue genQueue(ModuleProperties module) {
        ModuleProperties.Queue queue = module.getQueue();
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
