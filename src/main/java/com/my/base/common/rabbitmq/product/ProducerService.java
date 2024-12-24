package com.my.base.common.rabbitmq.product;

/**
 * 生产者接口
 */
public interface ProducerService {

 /**
  * 发送消息
  * @param message
  */
 void send(Object message);

}
