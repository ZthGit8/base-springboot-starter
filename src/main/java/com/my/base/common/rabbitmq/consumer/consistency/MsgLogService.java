package com.my.base.common.rabbitmq.consumer.consistency;

import com.my.base.common.rabbitmq.domain.MsgLog;

import java.util.List;

/**
 * @author
 * @date 2025/3/5 16:02
 * @description: 实现该接口可以自定义消息日志的存储方式
 *
 */
public interface MsgLogService {
    /**
     * 查询消费失败的消息
     * @return
     */
    List<MsgLog> selectFailMsg();

    /**
     * 查询数据信息
     * @param msgId
     * @return
     */
    MsgLog selectByMsgId(String msgId);

    /**
     * 保存消息日志
     * @param exchange
     * @param routeKey
     * @param queueName
     * @param msgId
     * @param object
     * @return
     */

    void save(String exchange, String routeKey, String queueName, String msgId, Object object);

    /**
     * 更新状态
     * @param msgId
     * @param status
     * @param result
     */

    void updateStatus(String msgId, Integer status, String result);

    /**
     * 更新下次重试时间
     * @param msgId
     * @param currentTryCount
     */

    void updateNextTryTime(String msgId, Integer currentTryCount);

    /**
     * 构建消息日志
     * @param exchange
     * @param routeKey
     * @param queueName
     * @param msgId
     * @param object
     * @return
     */
    MsgLog buildMsgLog(String exchange, String routeKey, String queueName, String msgId, Object object);
}
