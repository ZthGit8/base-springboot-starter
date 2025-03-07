package com.my.base.common.task;

import cn.hutool.extra.spring.SpringUtil;
import com.my.base.common.rabbitmq.constants.Constant;
import com.my.base.common.rabbitmq.consumer.consistency.MsgLogService;
import com.my.base.common.rabbitmq.domain.MsgLog;
import com.my.base.common.rabbitmq.helper.MsgHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@ConditionalOnProperty(name = "spring.rabbitmq.consistency", havingValue = "true")
public class ScheduledTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTask.class);

    /**
     * 最大投递次数
     */
    private static final int MAX_TRY_COUNT = 3;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 每30s拉取消费失败的消息, 重新投递
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void retry() {
        LOGGER.info("开始执行重新投递消费失败的消息！");
        // 查询需要重新投递的消息
        MsgLogService msgLogService = SpringUtil.getBean(MsgLogService.class);
        if (msgLogService == null) {
            return;
        }
        List<MsgLog> msgLogs = msgLogService.selectFailMsg();
        for (MsgLog msgLog : msgLogs) {
            if (msgLog.getTryCount() >= MAX_TRY_COUNT) {
                msgLogService.updateStatus(msgLog.getMsgId(), Constant.RETRY_FAIL, msgLog.getResult());
                LOGGER.info("超过最大重试次数, msgId: {}", msgLog.getMsgId());
                break;
            }

            // 重新投递消息
            CorrelationData correlationData = new CorrelationData(msgLog.getMsgId());
            rabbitTemplate.convertAndSend(msgLog.getExchange(), msgLog.getQueueName(), MsgHelper.objToMsg(msgLog.getMsg()), correlationData);
            // 更新下次重试时间
            msgLogService.updateNextTryTime(msgLog.getMsgId(), msgLog.getTryCount());
        }
    }
}

