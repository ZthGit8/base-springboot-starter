package com.my.base.infra.mq.rabbitmq.domain;

import com.my.base.infra.mq.rabbitmq.consumer.ConsumerService;
import lombok.Data;

import java.util.Map;

@Data
public class MQSendParams {

    /**
     * 正文不能为空
     */
    private Object content;

    /**
     * 消息id
     */
    private String msgId;

    /**
     * 队列名
     */
    private String queue;

    /**
     * 路由Key
     */
    private String routingKey;

    /**
     * 交换机名
     */
    private String exchange;

    /**
     * 消费者，可以是 bean 名称和 class 类名 (跨项目无法使用指定消费者，使用注解监听队列方式就行)
     */
    private ConsumerService consumer;

    /**
     * 最大重试次数
     */
    private int maxAttempts = 5;

    /**
     * 是否自动确认
     */
    private boolean autoAck;

    /**
     * 是否持久化
     */
    private boolean durable = true; // 默认true持久化，重启消息不会丢失

    /**
     * 是否具有排他性
     */
    private boolean exclusive = false; // 默认false，可多个消费者消费同一个队列

    /**
     * 当消费者均断开连接，是否自动删除队列
     */
    private boolean autoDelete = false; // 默认false,不自动删除，避免消费者断开队列丢弃消息

    /**
     * 绑定死信队列的交换机名称
     */
    private String deadLetterExchange;

    /**
     * 绑定死信队列的路由key
     */
    private String deadLetterRoutingKey;

    /**
     * 交换机其他参数
     */
    private Map<String, Object> arguments;

}
