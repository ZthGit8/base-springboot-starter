package com.my.base.infra.mq.rabbitmq.retry;


import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;

/**
 * 重试处理器
 */
public interface CustomRetryListener {

    /**
     * 最后一次重试失败回调
     *
     * @param context
     * @param callback
     * @param throwable
     * @param <E>
     * @param <T>
     */
    <E extends Throwable, T> void lastRetry(RetryContext context, RetryCallback<T, E> callback, Throwable throwable);

    /**
     * 每次失败的回调
     *
     * @param context
     * @param callback
     * @param throwable
     * @param <E>
     * @param <T>
     */
    <E extends Throwable, T> void onRetry(RetryContext context, RetryCallback<T, E> callback, Throwable throwable);
}
