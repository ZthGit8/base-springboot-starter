package com.my.base.shared.function;

import java.io.Serializable;

/**
 * @author
 * @date 2025/12/2 11:16
 * @description:
 */
// 支持序列化的双参数函数接口，Lambda要符合这个格式
public interface SerialBiFunction<T, U, R> extends Serializable {
    // 方法格式：传入T（Service实例）和U（参数），返回R（结果）
    R apply(T t, U u);
}