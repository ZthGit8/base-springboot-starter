package com.my.base.common.storage.log;

/**
 * MQ消息消费失败日志存储实现类，需要存储日志实现该接口进行具体的入库操作，需要注入实现类交给spring管理
 * @author zhengtianhan
 */
public interface MQFailLogStorage {
    /**
     * 保存消费失败日志
     * @param exchange
     * @param route
     * @param replyCode
     * @param replyText
     * @param message
     */
    void save(String exchange, String route, int replyCode, String replyText, String message);
}
