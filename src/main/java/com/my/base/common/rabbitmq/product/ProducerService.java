package com.my.base.common.rabbitmq.product;


import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 生产者接口
 */
public interface ProducerService<T> {


 /**
  *
  * @param message  发送的消息
  * @param confirmCallback 发布确认
  * @param returnCallback 返回确认
  */
 void sendMsg(T message, RabbitTemplate.ConfirmCallback confirmCallback, RabbitTemplate.ReturnsCallback returnCallback);

}
