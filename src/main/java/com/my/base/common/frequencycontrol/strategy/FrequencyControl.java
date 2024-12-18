package com.my.base.common.frequencycontrol.strategy;



import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;

import java.util.Map;

public interface FrequencyControl<K extends FrequencyControlDTO> {
    /**
     * 是否达到限流阈值 子类实现 每个子类都可以自定义自己的限流逻辑判断
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     * @return true-方法被限流 false-方法没有被限流
     */
    boolean reachRateLimit(Map<String, K> frequencyControlMap);

    /**
     * 增加限流统计次数 子类实现 每个子类都可以自定义自己的限流统计信息增加的逻辑
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     */
    void addFrequencyControlStatisticsCount(Map<String, K> frequencyControlMap);

}
