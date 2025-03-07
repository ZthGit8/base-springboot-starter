package com.my.base.common.rabbitmq.consumer.customize;

import com.my.base.common.rabbitmq.consumer.ConsumerService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;


import java.io.IOException;

/**
 * @date 2023年04月18日 17:53
 */
@Slf4j
public abstract class AbsConsumerService implements ConsumerService {

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        String body = new String(message.getBody());
        onConsumer(body, message, channel);
    }

    /**
     * 扩展消费方法，对消息进行封装
     *
     * @param data
     * @throws IOException
     */
    public void onConsumer(String data, Message message, Channel channel) throws IOException {
        log.error("未对此方法进行实现: {}", data);
    }

    /**
     * 确认消息
     */
    protected void ack(Message message, Channel channel) throws IOException {
        ack(Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     */
    protected void nack(Message message, Channel channel) throws IOException {
        nack(Boolean.FALSE, Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     */
    protected void basicReject(Message message, Channel channel) throws IOException {
        basicReject(Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    protected void basicReject(Boolean multiple, Message message, Channel channel) throws IOException {
        channel.basicReject(message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 是否自动确认
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    protected void ack(Boolean multiple, Message message, Channel channel) throws IOException {
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 拒绝消息
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     * @param requeue  当前 DeliveryTag 消息是否重回队列 true 是 false 否
     */
    protected void nack(Boolean multiple, Boolean requeue, Message message, Channel channel) throws IOException {
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), multiple, requeue);
    }
}
