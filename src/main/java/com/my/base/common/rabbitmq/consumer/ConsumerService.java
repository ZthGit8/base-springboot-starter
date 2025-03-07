package com.my.base.common.rabbitmq.consumer;

import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * 消费者接口
 */
public interface ConsumerService extends ChannelAwareMessageListener {

}
