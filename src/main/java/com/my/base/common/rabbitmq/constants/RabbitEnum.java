package com.my.base.common.rabbitmq.constants;


import lombok.Getter;

/**
 * 队列，交换机。路由 常量枚举
 */
@Getter
public enum  RabbitEnum {

    QUEUE("{}.queue", "队列名称"),

    EXCHANGE("{}.exchange", "交换机名称"),

    ROUTER_KEY("{}.key", "路由名称"),
    ;

    RabbitEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    private final String value;

    private final String desc;

}
