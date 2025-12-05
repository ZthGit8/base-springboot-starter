package com.my.base.features.ratelimit.service;



import com.my.base.error.FrequencyControlException;
import com.my.base.features.ratelimit.domain.FrequencyControlDTO;
import com.my.base.features.ratelimit.strategy.FrequencyControl;
import com.my.base.web.response.ResultCode;
import com.my.base.shared.util.AssertUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抽象类频控服务 其他类如果要实现限流服务 直接注入使用通用限流类 后期会通过继承此类实现令牌桶等算法
 *
 * @param <K>
 */
@Slf4j
public abstract class AbstractFrequencyControlService<K extends FrequencyControlDTO> 
    implements FrequencyControl<K> {

    /**
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     * @param supplier            函数式入参-代表每个频控方法执行的不同的业务逻辑
     * @return 业务方法执行的返回值
     * @throws Throwable
     */
    private <T> T executeWithFrequencyControlMap(Map<String, K> frequencyControlMap, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        if (reachRateLimit(frequencyControlMap)) {
            throw new FrequencyControlException(ResultCode.FORBIDDEN_ACCESS.getCode(),ResultCode.FORBIDDEN_ACCESS.getMessage());
        }
        try {
            return supplier.get();
        } finally {
            //不管成功还是失败，都增加次数
            addFrequencyControlStatisticsCount(frequencyControlMap);
        }
    }


    /**
     * 多限流策略的编程式调用方法 无参的调用方法
     *
     * @param frequencyControlList 频控列表 包含每一个频率控制的定义以及顺序
     * @param supplier             函数式入参-代表每个频控方法执行的不同的业务逻辑
     * @return 业务方法执行的返回值
     * @throws Throwable 被限流或者限流策略定义错误
     */
    public <T> T executeWithFrequencyControlList(List<K> frequencyControlList, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        boolean existsFrequencyControlHasNullKey = frequencyControlList.stream().anyMatch(frequencyControl -> ObjectUtils.isEmpty(frequencyControl.getKey()));
        AssertUtil.isFalse(existsFrequencyControlHasNullKey, "限流策略的Key字段不允许出现空值");
        // 去重，一个key只能有一个限流规则
        Map<String, K> frequencyControlDTOMap = frequencyControlList.stream().collect(Collectors.groupingBy(K::getKey, Collectors.collectingAndThen(Collectors.toList(), List::getFirst)));
        return executeWithFrequencyControlMap(frequencyControlDTOMap, supplier);
    }

    /**
     * 单限流策略的调用方法-编程式调用
     *
     * @param frequencyControl 单个频控对象
     * @param supplier         服务提供着
     * @return 业务方法执行结果
     * @throws Throwable
     */
    public <T> T executeWithFrequencyControl(K frequencyControl, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        return executeWithFrequencyControlList(Collections.singletonList(frequencyControl), supplier);
    }


    @FunctionalInterface
    public interface SupplierThrowWithoutParam<T> {

        /**
         * Gets a result.
         *
         * @return a result
         */
        T get() throws Throwable;
    }

    @FunctionalInterface
    public interface Executor {

        /**
         * Gets a result.
         *
         * @return a result
         */
        void execute() throws Throwable;
    }

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    protected abstract String getStrategyName();

}
