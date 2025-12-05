package com.my.base.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author
 * @date 2025/1/3 11:57
 * @description: 第三方api注解，进行验签安全校验
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OtaApi {
    /**
     * timestamp 相隔多少时间拒绝请求 单位秒 默认60s
     * @return
     */
    long keepTime() default 60L;

    /**
     * nonce有效时间
     * @return
     */
    long nonceMaxTime() default 60L;
}
