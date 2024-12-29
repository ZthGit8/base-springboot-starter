package com.my.base.common.rabbitmq.comsumer;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 死信队列消费
 */
@Component(value = "deadConsumerService")
@Slf4j
public class DeadConsumerService extends AbsConsumerService {

    @Override
    public void onConsumer(String data, Message message, Channel channel) throws IOException {
        log.info("dead message is {}", data);
        // 手动确认ack
        ack(message, channel);
        //消费逻辑，处理死信消息
        //1.可以实现订单多少分钟自动取消
        //2.可以实现失败的的消息兜底操作
    }
}
