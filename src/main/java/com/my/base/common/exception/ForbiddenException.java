package com.my.base.common.exception;

public class ForbiddenException extends BaseException{
    public ForbiddenException(Integer code, String message) {
        super(code, message);
    }
}
