package com.my.base.common.result;

public enum ResultCode implements IResult{
    SUCCESS(200, "成功"),
    FAIL(400, "失败"),
    UNAUTHORIZED(401, "未认证"),
    NOT_FOUND(404, "未找到"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    PARAM_ERROR(601, "参数校验失败"),
    FORBIDDEN_ACCESS(602, "限制禁止访问"),
    DISABLE_OPERATION(604, "其他用户正再执行该方法");

    private Integer code;
    private String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
