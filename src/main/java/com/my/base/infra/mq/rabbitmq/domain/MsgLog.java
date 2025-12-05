package com.my.base.infra.mq.rabbitmq.domain;

import lombok.Data;

import java.util.Date;

@Data
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

}

