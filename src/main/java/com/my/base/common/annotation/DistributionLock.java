package com.my.base.common.annotation;

import com.my.base.common.enums.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 */
@Retention(RetentionPolicy.RUNTIME)//运行时生效
@Target(ElementType.METHOD)//作用在方法上
public @interface DistributionLock {
    /**
     * key的前缀,默认取方法全限定名，除非我们在不同方法上对同一个资源做分布式锁，就自己指定
     *
     * @return key的前缀
     */
    String prefixKey() default "";

    /**
     * springEl 表达式
     *
     * @return 表达式
     */
    String key();

    /**
     * 等待锁的时间，默认-1，不等待直接失败,redisson默认也是-1，COMMON_LOCK 不支持等待时间
     *
     * @return 单位秒
     */
    int waitTime() default -1;

    /**
     * 等待锁的时间单位，默认毫秒
     *
     * @return 单位
     */
    TimeUnit unit() default TimeUnit.MILLISECONDS;

    /**
     * 使用哪种锁，默认为轻量级的分布式锁，也可以使用redisson提供的锁，如：读写锁，信号量等
     *
     * @return 单位
     */
    String useLockType() default "COMMON_LOCK";

}
