package com.my.base.common.rabbitmq.comsumer;

import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * 消费者接口
 * @date 2023年04月11日 13:52
 */
public interface ConsumerService extends ChannelAwareMessageListener {

}
