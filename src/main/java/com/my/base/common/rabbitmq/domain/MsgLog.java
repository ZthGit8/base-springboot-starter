package com.my.base.common.rabbitmq.domain;

import java.util.Date;

public class MsgLog {

    /**
     * 消息唯一标识
     */
    private String msgId;

    /**
     * 交换机
     */
    private String exchange;

    /**
     * 路由键
     */
    private String routeKey;

    /**
     * 队列名称
     */
    private String queueName;

    /**
     * 消息体, json格式化
     */
    private String msg;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 状态，0：等待消费，1：消费成功，2：消费失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer tryCount;

    /**
     * 下一次重试时间
     */
    private Date nextTryTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    public String getMsgId() {
        return msgId;
    }

    public MsgLog setMsgId(String msgId) {
        this.msgId = msgId;
        return this;
    }

    public String getExchange() {
        return exchange;
    }

    public MsgLog setExchange(String exchange) {
        this.exchange = exchange;
        return this;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public MsgLog setRouteKey(String routeKey) {
        this.routeKey = routeKey;
        return this;
    }

    public String getQueueName() {
        return queueName;
    }

    public MsgLog setQueueName(String queueName) {
        this.queueName = queueName;
        return this;
    }

    public String getMsg() {
        return msg;
    }

    public MsgLog setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public MsgLog setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getTryCount() {
        return tryCount;
    }

    public MsgLog setTryCount(Integer tryCount) {
        this.tryCount = tryCount;
        return this;
    }

    public Date getNextTryTime() {
        return nextTryTime;
    }

    public MsgLog setNextTryTime(Date nextTryTime) {
        this.nextTryTime = nextTryTime;
        return this;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public MsgLog setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public MsgLog setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public String getResult() {
        return result;
    }

    public MsgLog setResult(String result) {
        this.result = result;
        return this;
    }
}

