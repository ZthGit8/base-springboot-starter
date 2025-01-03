package com.my.base.common.function;


import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的函数接口
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface SlFunction<T, R> extends Function<T, R>, Serializable {
}

