package com.my.base.error;

import lombok.Data;

@Data
public abstract class BaseException extends RuntimeException{
    private Integer code;
    private String message;

    public BaseException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
