package com.my.base.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T>{

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<T>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail() {
        return new Result<T>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage(), null);
    }

    public static <T> Result<T> fail(String massage) {
        return new Result<T>(ResultCode.FAIL.getCode(), massage, null);
    }

    public static <T> Result<T> fail(int code, String massage) {
        return new Result<T>(code, massage, null);
    }

    public static <T> Result<T> fail(T data) {
        return new Result<T>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage(), data);
    }

    public static <T> Result<T> fail(IResult iResult) {
        return new Result<T>(iResult.getCode(), iResult.getMessage(), null);
    }

}
