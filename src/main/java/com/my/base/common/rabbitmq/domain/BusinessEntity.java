package com.my.base.common.rabbitmq.domain;

/**
 * <p>
 * RabbitConfig
 * </p>
 * @author 程序员蜗牛
 */
public class BusinessEntity {


    /**
     * 正文不能为空
     */
    private String content;

    /**
     * 消息id
     */
    private String msgId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }
}
