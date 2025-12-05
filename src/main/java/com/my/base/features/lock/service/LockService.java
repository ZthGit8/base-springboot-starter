package com.my.base.features.lock.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface LockService {

     <T> T executeWithLockThrows(String key, int waitTime, TimeUnit unit, SupplierThrow<T> supplier) throws Throwable;

     <T> T executeWithLock(String key, int waitTime, TimeUnit unit, Supplier<T> supplier);

    String getLockType();

    @FunctionalInterface
     interface SupplierThrow<T> {

        /**
         * Gets a result.
         *
         * @return a result
         */
        T get() throws Throwable;
    }
}
