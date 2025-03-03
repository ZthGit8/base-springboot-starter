package com.my.base.common.service.mq;


import com.my.base.common.mapper.MsgLogMapper;
import com.my.base.common.rabbitmq.constants.Constant;
import com.my.base.common.rabbitmq.domain.MsgLog;
import com.my.base.common.utils.JsonUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * RabbitConfig
 * </p>
 * @author 程序员蜗牛
 */
@Service
public class MsgLogService {

    @Autowired
    private MsgLogMapper msgLogMapper;


    /**
     * 查询消费失败的消息
     * @return
     */
    public List<MsgLog> selectFailMsg() {
        return msgLogMapper.selectFailMsg();
    }


    /**
     * 查询数据信息
     * @param msgId
     * @return
     */
    public MsgLog selectByMsgId(String msgId) {
        return msgLogMapper.selectByPrimaryKey(msgId);
    }

    /**
     * 保存消息日志
     * @param exchange
     * @param routeKey
     * @param queueName
     * @param msgId
     * @param object
     * @return
     */
    @Transactional
    public void save(String exchange, String routeKey, String queueName, String msgId, Object object){
        MsgLog entity = buildMsgLog(exchange, routeKey, queueName, msgId, object);
        msgLogMapper.insert(entity);
    }

    /**
     * 更新状态
     * @param msgId
     * @param status
     * @param result
     */
    @Transactional
    public void updateStatus(String msgId, Integer status, String result){
        msgLogMapper.updateStatus(msgId, status, result);
    }

    /**
     * 更新下次重试时间
     * @param msgId
     * @param currentTryCount
     */
    @Transactional
    public void updateNextTryTime(String msgId, Integer currentTryCount) {
        MsgLog msgLog = new MsgLog();
        msgLog.setMsgId(msgId);
        msgLog.setNextTryTime(DateUtils.addMinutes(new Date(), currentTryCount));
        msgLogMapper.updateTryCount(msgLog);
    }


    private MsgLog buildMsgLog(String exchange, String routeKey, String queueName, String msgId, Object object){
        MsgLog target = new MsgLog();
        target.setMsgId(msgId);
        target.setExchange(exchange);
        target.setRouteKey(routeKey);
        target.setQueueName(queueName);
        target.setMsg(JsonUtil.toStr(object));
        target.setStatus(Constant.WAIT);
        target.setTryCount(0);
        target.setNextTryTime(DateUtils.addMinutes(new Date(), 1));
        target.setCreateTime(new Date());
        target.setUpdateTime(new Date());
        return target;
    }
}
