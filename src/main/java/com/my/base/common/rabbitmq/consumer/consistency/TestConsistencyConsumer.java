package com.my.base.common.rabbitmq.consumer.consistency;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;

/**
 * @author
 * @date 2025/3/5 16:19
 * @description:
 */
@Slf4j
public class TestConsistencyConsumer extends AbsConsistencyConsumer {
    @RabbitListener(queues = {"business.queue"})
    @Override
    public void doConsume(Message message, Channel channel) {
        consumeFromAuto(message, channel, (msg) -> {
            log.info(JSONUtil.toJsonStr(new String(msg.getBody())));
            try {
                super.ack(message,channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        });
    }
}
