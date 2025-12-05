package com.my.base.infra.mq.rabbitmq.constants;

public final class Constant {

    /**
     * 等待消费
     */
    public final static Integer WAIT = 0;

    /**
     * 消费成功
     */
    public final static Integer SUCCESS = 1;

    /**
     * 消费失败
     */
    public final static Integer FAIL = 2;

    /**
     * 重试失败
     */
    public final static Integer RETRY_FAIL = 9;
}
