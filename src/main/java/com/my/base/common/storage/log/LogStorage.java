package com.my.base.common.storage.log;

import java.util.Map;

public interface LogStorage {
    void save(String traceId, String params);
}
