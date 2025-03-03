package com.my.base.common.mapper;

import com.my.base.common.rabbitmq.domain.MsgLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MsgLogMapper {

    /**
     * 插入消息日志
     * @param msgLog
     */
    void insert(MsgLog msgLog);

    /**
     * 更新消息状态
     * @param msgId
     */
    void updateStatus(@Param("msgId") String msgId, @Param("status") Integer status, @Param("result") String result);

    /**
     * 查询需要重新投递的消息
     * @return
     */
    List<MsgLog> selectFailMsg();

    /**
     * 更新重试此时
     * @param msgLog
     */
    void updateTryCount(MsgLog msgLog);

    /**
     * 查询消息信息
     * @param msgId
     * @return
     */
    MsgLog selectByPrimaryKey(String msgId);
}
