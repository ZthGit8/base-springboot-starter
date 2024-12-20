package com.my.base.common.storage.log;

/**
 * @author EDY
 * @date 2024/12/20 15:18
 * @description:
 */
public interface SlowSqlLogStorage {
    void save(String sql, long time);
}
