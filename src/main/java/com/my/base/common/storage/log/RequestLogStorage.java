package com.my.base.common.storage.log;

/**
 * 日志存储实现类，需要存储日志实现该接口进行具体的入库操作，需要注入实现类交给spring管理
 * @author zhengtianhan
 */
public interface RequestLogStorage {
    void save(String traceId, String params);
}
