package com.my.base.common.exception;

public class BusinessException extends BaseException{

    public BusinessException(Integer code, String message) {
        super(code,message);
    }


}
