package com.my.base.common.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.my.base.common.exception.BaseException;
import com.my.base.common.result.Result;
import com.my.base.common.result.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import javax.validation.ConstraintViolationException;
import java.lang.reflect.InvocationTargetException;

@Slf4j
@RestControllerAdvice()
public class GlobalExceptionAdvice {
    @ExceptionHandler({Throwable.class})
    public Result<String> exceptionHandle(Throwable e, HttpServletRequest request, HttpServletResponse response) {
        Class<?> exceptionClass = e.getClass();
        String errorMsg = e.getMessage();
        if (exceptionClass.equals(MethodArgumentNotValidException.class)) {
            BindingResult bindingResult = ((MethodArgumentNotValidException) e).getBindingResult();
            StringBuilder sb = new StringBuilder("校验失败:");
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                sb.append(fieldError.getField()).append("：").append(fieldError.getDefaultMessage()).append(", ");
            }
            errorMsg = sb.toString();
            response.setStatus(ResultCode.PARAM_ERROR.getCode());
        } else if (exceptionClass.equals(ConstraintViolationException.class)) {

            response.setStatus(ResultCode.PARAM_ERROR.getCode());
        }else if (exceptionClass.equals(HttpMediaTypeNotAcceptableException.class)) {
            response.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
        } else if (exceptionClass.equals(HttpMediaTypeNotSupportedException.class)) {
            response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        } else if (exceptionClass.equals(HttpRequestMethodNotSupportedException.class)) {
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        } else if (exceptionClass.equals(NoResourceFoundException.class)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            log.warn(e.getMessage());
        } else if (exceptionClass.getSuperclass().equals(BaseException.class)) {
            try {
                response.setStatus((int) ReflectionUtils.findMethod(exceptionClass, "getCode").invoke(e));
            } catch (InvocationTargetException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            if (exceptionClass.equals(AsyncRequestTimeoutException.class)) {
                return null;
            }

            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            log.error(e.getMessage(), e);
        }

        return Result.fail(response.getStatus(),errorMsg);
    }
}
