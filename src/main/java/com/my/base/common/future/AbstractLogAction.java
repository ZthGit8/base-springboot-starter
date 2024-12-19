package com.my.base.common.future;

import com.my.base.common.exception.BaseException;
import com.my.base.common.utils.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractLogAction<R> {
    protected final String methodName;
    protected final Object[] args;

    public AbstractLogAction(String methodName, Object... args) {
        this.methodName = methodName;
        this.args = args;
    }

    protected void logResult(R result, Throwable throwable) {
        if (throwable != null) {
            boolean isBusinessError = throwable instanceof BaseException || (throwable.getCause() != null && throwable
                    .getCause() instanceof BaseException);
            if (isBusinessError) {
                logBusinessError(throwable);
            } else {
                log.error("{} unknown error, param:{} , error:{}", methodName, args, ExceptionUtils.extractRealException(throwable));
            }
        } else {

            log.info("{} param:{} , result:{}", methodName, args, result);

        }
    }

    private void logBusinessError(Throwable throwable) {
        log.error("{} business error, param:{} , error:{}", methodName, args, throwable.toString(), ExceptionUtils.extractRealException(throwable));
    }


}