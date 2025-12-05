package com.my.base.aop;

import com.my.base.shared.annotation.FeignRetry;
import com.my.base.web.context.RequestContext;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Slf4j
@Component
public class FeignRetryAspect {

    @Around("@annotation(com.my.base.shared.annotation.FeignRetry)")
    public Object retry(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getCurrentMethod(joinPoint);
        FeignRetry feignRetry = method.getAnnotation(FeignRetry.class);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(prepareBackOffPolicy(feignRetry));
        retryTemplate.setRetryPolicy(prepareSimpleRetryPolicy(feignRetry));

        return retryTemplate.execute(retryContext -> {
                    int retryCount = retryContext.getRetryCount();
                    log.info("Sending request method: {}, max attempt: {}, delay: {}, retryCount: {}",
                            method.getName(),
                            feignRetry.maxAttempt(),
                            feignRetry.backoff().delay(),
                            retryCount
                    );
                    return joinPoint.proceed(joinPoint.getArgs());
                },
                retryContext -> {
                    //重试失败后执行的代码
                    log.error("retry {} count error: traceId={}", retryContext.getRetryCount(), RequestContext.getRequestInfo().getTraceId());
                    return "failed callback";
                }
        );


    }

    private BackOffPolicy prepareBackOffPolicy(FeignRetry feignRetry) {
        if (feignRetry.backoff().multiplier() != 0) {
            ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
            backOffPolicy.setInitialInterval(feignRetry.backoff().delay());
            backOffPolicy.setMaxInterval(feignRetry.backoff().maxDelay());
            backOffPolicy.setMultiplier(feignRetry.backoff().multiplier());
            return backOffPolicy;
        } else {
            FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
            fixedBackOffPolicy.setBackOffPeriod(feignRetry.backoff().delay());
            return fixedBackOffPolicy;
        }
    }


    private SimpleRetryPolicy prepareSimpleRetryPolicy(FeignRetry feignRetry) {
        Map<Class<? extends Throwable>, Boolean> policyMap = new HashMap<>();
        policyMap.put(RetryableException.class, true);
        policyMap.put(IOException.class, true);
        policyMap.put(ConnectException.class, true);
        policyMap.put(SocketTimeoutException.class, true);
        for (Class<? extends Throwable> t : feignRetry.include()) {
            policyMap.put(t, true);
        }
        return new SimpleRetryPolicy(feignRetry.maxAttempt(), policyMap, true);
    }

    private Method getCurrentMethod(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }
}