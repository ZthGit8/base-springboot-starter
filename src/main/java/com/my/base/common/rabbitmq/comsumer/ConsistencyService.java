package com.my.base.common.rabbitmq.comsumer;


import com.my.base.common.rabbitmq.constants.Constant;
import com.my.base.common.rabbitmq.domain.BusinessEntity;
import com.my.base.common.rabbitmq.domain.MsgLog;
import com.my.base.common.service.mq.BussinessService;
import com.my.base.common.service.mq.MsgLogService;
import com.my.base.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * <p>
 * SpringBoot整合RabbitMQ实现业务异步执行
 * 可靠生产和可靠消费保证分布式事务的一致性
 * </p>
 *
 * @author 程序员蜗牛
 */
@Component
public class ConsistencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    @Autowired
    private BussinessService bussinessService;

    @Autowired
    private MsgLogService msgLogService;


    /**
     * 监听消息队列，自动确认模式，无需调用ack或者nack方法，当程序执行时才删除消息
     * 配置参数：spring.rabbitmq.listener.simple.acknowledge-mode=auto
     *
     * @param message
     */
    @RabbitListener(queues = {"bussiness.queue"})
    public void consumeFromAuto(Message message) {
        LOGGER.info("收到消息：{}", message.toString());
        // 获取消息ID
        BusinessEntity entity = msgToObj(message, BusinessEntity.class);
        // 消息幂等性处理，如果已经处理成功，无需重复消费
        MsgLog queryObj = msgLogService.selectByMsgId(entity.getMsgId());
        if (Objects.nonNull(queryObj) && Constant.SUCCESS.equals(queryObj.getStatus())) {
            return;
        }
        // 执行业务逻辑
        boolean success = bussinessService.doService();
        if (success) {
            msgLogService.updateStatus(entity.getMsgId(), Constant.SUCCESS, "业务执行成功");
        } else {
            msgLogService.updateStatus(entity.getMsgId(), Constant.FAIL, "业务执行失败");
        }
    }

    /**
     * 将对象序列化成消息数据
     * @param obj
     * @return
     */
    public static Message objToMsg(Object obj) {
        if (null == obj) {
            return null;
        }
        if(obj instanceof String){

        }

        Message message = MessageBuilder.withBody(JsonUtil.toStr(obj).getBytes()).build();
        // 设置消息持久化
        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        // 设置消息为json格式
        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return message;
    }

    /**
     * 将消息数据反序列化成对象
     * @param message
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> T msgToObj(Message message, Class<T> clazz) {
        if (null == message || null == clazz) {
            return null;
        }

        String json = new String(message.getBody());
        return JsonUtil.toObj(json, clazz);
    }
}
