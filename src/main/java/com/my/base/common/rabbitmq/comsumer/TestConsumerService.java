package com.my.base.common.rabbitmq.comsumer;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component(value = "testConsumerService")
public class TestConsumerService extends AbsConsumerService {

    @Override
    public void onConsumer(String data, Message message, Channel channel) throws IOException {
        log.info("data is {}", data);
        nack(message, channel);
    }
}
