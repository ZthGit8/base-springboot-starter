package com.my.base.common.rabbitmq.producer;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 生产者实现类
 */
@Slf4j
@Data
public abstract class AbsProducerService<T> implements ProducerService<T> {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 交换机
     */
    private String exchange;

    /**
     * 路由
     */
    private String routingKey;


    public abstract void send(T msg);

    public void sendQueue(T msg) {
        sendQueue(msg, null,null);
    };

    public void sendQueue(T msg,RabbitTemplate.ConfirmCallback callback) {
        sendQueue(msg, callback,null);
    }

    public void sendQueue(T msg,RabbitTemplate.ReturnsCallback returns) {
        sendQueue(msg, null,returns);
    }

    /**
     * 使用RabbitMQ发送消息的方法
     *
     * @param msg      消息内容，可以是任意对象，但需要能够被序列化为JSON字符串
     * @param callback 发送消息的确认回调，用于接收消息发送确认信息
     * @param returns  发送消息的返回回调，用于接收消息发送返回信息
     */
    public void sendQueue(T msg, RabbitTemplate.ConfirmCallback callback, RabbitTemplate.ReturnsCallback returns) {
        // 创建一个消息后处理器，用于在消息发送前设置一些额外的属性
        MessagePostProcessor messagePostProcessor = (message) -> {
            // 获取消息的属性对象
            MessageProperties messageProperties = message.getMessageProperties();
            // 设置消息的唯一ID
            messageProperties.setMessageId(IdUtil.randomUUID());
            // 设置消息的时间戳
            messageProperties.setTimestamp(new Date());
            return message;
        };

        // 设置回调处理
        setConfirmCallBack(callback);
        setReturnsCallBack(returns);
        // 创建一个新的消息属性对象，用于设置消息的编码和类型
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setContentType("text/plain");

        // 将消息对象转换为JSON字符串
        String data = JSONUtil.toJsonStr(msg);

        // 创建一个消息对象，包含消息内容和属性
        Message message = new Message(data.getBytes(StandardCharsets.UTF_8), messageProperties);

        // 使用RabbitMQ的模板类发送消息，并使用前面定义的消息后处理器进行处理
        rabbitTemplate.convertAndSend(this.exchange, this.routingKey, message, messagePostProcessor);
    }


    /**
     * 设置消息发送到MQ的回调处理
     *
     * @param callback
     */
    private void setConfirmCallBack(RabbitTemplate.ConfirmCallback callback) {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                if (callback != null) {
                    // 消息发送到队列未成功
                    callback.confirm(correlationData, ack, cause);
                }
                if (correlationData != null) {
                    log.error("消息发送到Exchange失败, {}, cause: {}", correlationData, cause);
                }
            }
        });

    }

    /**
     * 设置消息发送到队列失败的回调处理
     * @param returns
     */
    private void setReturnsCallBack(RabbitTemplate.ReturnsCallback returns) {
        rabbitTemplate.setReturnsCallback(returns);
    }

}
