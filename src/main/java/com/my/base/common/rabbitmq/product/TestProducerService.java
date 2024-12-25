package com.my.base.common.rabbitmq.product;

import org.springframework.stereotype.Component;

@Component("testProducerService")
public class TestProducerService extends AbsProducerService {

    @Override
    public void send() {
       this.sendMsg("hello");
    }
}
