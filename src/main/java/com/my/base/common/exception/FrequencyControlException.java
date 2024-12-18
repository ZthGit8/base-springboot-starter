package com.my.base.common.exception;


/**
 * 自定义限流异常
 */
public class FrequencyControlException extends BaseException {

    public FrequencyControlException(Integer code, String message) {
        super(code, message);
    }
}
