package com.my.base.common.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum LockType {
    /**
     * 普通的轻量级分布式锁
     */
    COMMON_LOCK(1, "COMMON_LOCK"),
    /**
     * redisson实现的分布式锁
     */
    REDISSON_LOCK(2, "REDISSON_LOCK");

    private int value;
    private String type;


    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
