package com.my.base.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁的key
 */
@Retention(RetentionPolicy.RUNTIME)//运行时生效
@Target(ElementType.FIELD)
public @interface LockField {
}
