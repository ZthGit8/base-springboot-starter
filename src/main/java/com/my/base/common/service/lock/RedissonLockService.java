package com.my.base.common.service.lock;

import com.my.base.common.enums.LockType;
import com.my.base.common.exception.BusinessException;
import com.my.base.common.result.ResultCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
public class RedissonLockService implements LockService{

    @Autowired
    private RedissonClient redissonClient;

    public <T> T executeWithLockThrows(String key, int waitTime, TimeUnit unit, LockService.SupplierThrow<T> supplier) throws Throwable {
        RLock lock = redissonClient.getLock(key);
        boolean lockSuccess = lock.tryLock(waitTime, unit);
        if (!lockSuccess) {
            throw new BusinessException(ResultCode.DISABLE_OPERATION.getCode(), ResultCode.DISABLE_OPERATION.getMessage());
        }
        try {
            return supplier.get();//执行锁内的代码逻辑
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @SneakyThrows
    public <T> T executeWithLock(String key, int waitTime, TimeUnit unit, Supplier<T> supplier) {
        return executeWithLockThrows(key, waitTime, unit, supplier::get);
    }

    @Override
    public String getLockType() {
        return LockType.REDISSON_LOCK.getType();
    }

    public <T> T executeWithLock(String key, Supplier<T> supplier) {
        return executeWithLock(key, -1, TimeUnit.MILLISECONDS, supplier);
    }


}