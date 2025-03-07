package com.my.base.common.rabbitmq.producer.customize;

import org.springframework.stereotype.Component;
@Component(value = "testProducerService")
public class TestProducerService<T> extends AbsProducerService<T> {

    @Override
    public void send(T msg) {
       this.sendQueue(msg);
    }

}
