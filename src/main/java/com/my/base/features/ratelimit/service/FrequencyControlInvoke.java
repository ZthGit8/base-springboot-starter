package com.my.base.features.ratelimit.service;

import com.my.base.features.ratelimit.domain.FrequencyControlDTO;
import com.my.base.shared.util.AssertUtil;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 限流工具类 提供编程式的限流调用方法
 */
public final class FrequencyControlInvoke {

    /**
     * 单限流策略的调用方法-编程式调用
     *
     * @param strategyName     策略名称
     * @param frequencyControl 单个频控对象
     * @param supplier         服务提供着
     * @return 业务方法执行结果
     * @throws Throwable
     */
    public static <T, K extends FrequencyControlDTO> T execute(
            String strategyName, 
            K frequencyControl, 
            AbstractFrequencyControlService.SupplierThrowWithoutParam<T> supplier) throws Throwable {
        
        AbstractFrequencyControlService<K> strategy = 
            (AbstractFrequencyControlService<K>) FrequencyControlStrategyFactory.getStrategy(strategyName);
        return strategy.executeWithFrequencyControl(frequencyControl, supplier);
    }

    public static <T, K extends FrequencyControlDTO> T executeWithList(
            String strategyName, 
            List<K> frequencyControlList, 
            AbstractFrequencyControlService.SupplierThrowWithoutParam<T> supplier) throws Throwable {
            
        AssertUtil.isFalse(
            frequencyControlList.stream().anyMatch(fc -> ObjectUtils.isEmpty(fc.getKey())), 
            "限流策略的Key字段不允许出现空值"
        );

        AbstractFrequencyControlService<K> strategy = 
            (AbstractFrequencyControlService<K>) FrequencyControlStrategyFactory.getStrategy(strategyName);
        
        if (strategy == null) {
            throw new IllegalArgumentException("未找到名为 " + strategyName + " 的限流策略");
        }
        
        return strategy.executeWithFrequencyControlList(frequencyControlList, supplier);
    }

    /**
     * 构造器私有
     */
    private FrequencyControlInvoke() {

    }

}
