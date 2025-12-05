package com.my.base.shared.async;

import com.my.base.error.BaseException;
import com.my.base.shared.util.ExceptionUtil;
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
                log.error("{} unknown error, param:{}", methodName, args,ExceptionUtil.extractRealException(throwable));
            }
        } else {

            log.info("{} param:{} , result:{}", methodName, args, result);

        }
    }

    private void logBusinessError(Throwable throwable) {
        log.error("{} business error, param:{} , error:{}", methodName, args, throwable.toString(), ExceptionUtil.extractRealException(throwable));
    }


}