package com.my.base.error;

public class BusinessException extends BaseException{

    public BusinessException(Integer code, String message) {
        super(code,message);
    }


}
