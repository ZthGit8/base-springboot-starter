package com.my.base.features.lock.service;

import com.my.base.shared.enums.LockType;
import com.my.base.error.BusinessException;
import com.my.base.web.response.ResultCode;
import com.my.base.shared.util.RedisUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class CommonLockService implements LockService {

    private static final String LOCK_VALUE = "lock_value";

    public <T> T executeWithLockThrows(String key, int waitTime, TimeUnit unit, SupplierThrow<T> supplier) throws Throwable {
        boolean lockSuccess = tryGetLock(key);
        if (!lockSuccess) {
            throw new BusinessException(ResultCode.DISABLE_OPERATION.getCode(), ResultCode.DISABLE_OPERATION.getMessage());
        }
        try {
            return supplier.get();//执行锁内的代码逻辑
        } finally {
            releaseLock(key);
        }
    }

    @SneakyThrows
    public <T> T executeWithLock(String key, int waitTime, TimeUnit unit, Supplier<T> supplier) {
        return executeWithLockThrows(key, waitTime, unit, supplier::get);
    }

    @Override
    public String getLockType() {
        return LockType.COMMON_LOCK.getType();
    }

    public <T> T executeWithLock(String key, Supplier<T> supplier) {
        return executeWithLock(key, -1, TimeUnit.MILLISECONDS, supplier);
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryGetLock(String key) {
        return Boolean.TRUE.equals(RedisUtil.setIfAbsent(key, LOCK_VALUE));
    }

    /**
     * 释放锁
     * @param key
     * @return
     */
    private boolean releaseLock(String key) {
        return RedisUtil.releaseLock(key, LOCK_VALUE);
    }


}