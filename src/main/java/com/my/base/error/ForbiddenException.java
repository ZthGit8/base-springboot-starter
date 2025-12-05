package com.my.base.error;

public class ForbiddenException extends BaseException{
    public ForbiddenException(Integer code, String message) {
        super(code, message);
    }
}
