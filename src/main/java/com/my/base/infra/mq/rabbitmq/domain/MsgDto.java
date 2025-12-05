package com.my.base.infra.mq.rabbitmq.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MsgDto {
    /**
     * 正文不能为空
     */
    private Object content;

    /**
     * 消息id
     */
    private String msgId;

}