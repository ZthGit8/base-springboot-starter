package com.my.base.infra.mq.rabbitmq.consumer.consistency;


import cn.hutool.extra.spring.SpringUtil;
import com.my.base.infra.mq.rabbitmq.constants.Constant;
import com.my.base.infra.mq.rabbitmq.domain.MsgDto;
import com.my.base.infra.mq.rabbitmq.domain.MsgLog;
import com.my.base.infra.mq.rabbitmq.helper.MsgHelper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * <p>
 * SpringBoot整合RabbitMQ实现业务异步执行
 * 可靠生产和可靠消费保证分布式事务的一致性
 * </p>
 *
 */
@Slf4j
@Component
public abstract class AbsConsistencyConsumer  {

    public abstract void doConsume(Message message, Channel channel);

    /**
     * 监听消息队列，自动确认模式，无需调用ack或者nack方法，当程序执行时才删除消息
     * 配置参数：spring.rabbitmq.listener.simple.acknowledge-mode=auto
     *
     * @param message
     */
    public void consumeFromAuto(Message message, Channel channel, Function<Message, Boolean> function) {
        log.info("收到消息：{}", message.toString());
        // 获取消息ID
        MsgDto entity = MsgHelper.msgToObj(message, MsgDto.class);
        MsgLogService msgLogService = SpringUtil.getBean(MsgLogService.class);
        // 消息幂等性处理，如果已经处理成功，无需重复消费
        MsgLog queryObj = msgLogService.selectByMsgId(entity.getMsgId());
        if (Objects.nonNull(queryObj) && Constant.SUCCESS.equals(queryObj.getStatus())) {
            return;
        }
        // 执行业务逻辑
        Boolean success = function.apply(message);
        if (success) {
            msgLogService.updateStatus(entity.getMsgId(), Constant.SUCCESS, "业务执行成功");
        } else {
            msgLogService.updateStatus(entity.getMsgId(), Constant.FAIL, "业务执行失败");
        }
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
    public void ack(Message message, Channel channel) throws IOException {
        ack(Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     */
    public void nack(Message message, Channel channel) throws IOException {
        nack(Boolean.FALSE, Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     */
    public void basicReject(Message message, Channel channel) throws IOException {
        basicReject(Boolean.FALSE, message, channel);
    }

    /**
     * 拒绝消息
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    public void basicReject(Boolean multiple, Message message, Channel channel) throws IOException {
        channel.basicReject(message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 是否自动确认
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    public void ack(Boolean multiple, Message message, Channel channel) throws IOException {
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 拒绝消息
     *
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     * @param requeue  当前 DeliveryTag 消息是否重回队列 true 是 false 否
     */
    public void nack(Boolean multiple, Boolean requeue, Message message, Channel channel) throws IOException {
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), multiple, requeue);
    }

}
