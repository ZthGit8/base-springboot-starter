package com.my.base.common.rabbitmq.constants;


import lombok.Getter;

/**
 * 队列，交换机。路由 常量枚举
 */
public enum  RabbitEnum {

    QUEUE("xxx.{}.queue", "队列名称"),

    EXCHANGE("xxx.{}.exchange", "交换机名称"),

    ROUTER_KEY("xxx.{}.key", "路由名称"),
    ;

    RabbitEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private String value;

    @Getter
    private String desc;

}
