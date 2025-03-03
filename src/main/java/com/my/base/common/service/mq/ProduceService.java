package com.my.base.common.service.mq;

import com.my.base.common.rabbitmq.comsumer.ConsistencyService;
import com.my.base.common.rabbitmq.domain.BusinessEntity;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * <p>
 * RabbitConfig
 * </p>
 * @author 程序员蜗牛
 */
@Service
public class ProduceService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MsgLogService msgLogService;

    /**
     * 发送消息
     */
    public boolean sendByAuto(BusinessEntity businessEntity) {
        String msgId = UUID.randomUUID().toString().replaceAll("-", "");
        businessEntity.setMsgId(msgId);
        // 1.存储要消费的数据
        msgLogService.save("bussiness.exchange",
                "bussiness.route",
                "bussiness.queue", msgId, businessEntity);

        // 2.发送消息到mq服务器中（附带消息ID）
        CorrelationData correlationData = new CorrelationData(msgId);
        rabbitTemplate.convertAndSend("bussiness.exchange", "bussiness.route",
                ConsistencyService.objToMsg(businessEntity), correlationData);
        return true;
    }
}
