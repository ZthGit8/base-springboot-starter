package com.my.base.common.service.frequencycontrol;



import com.my.base.common.exception.FrequencyControlException;
import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;
import com.my.base.common.frequencycontrol.strategy.FrequencyControl;
import com.my.base.common.result.ResultCode;
import com.my.base.common.utils.AssertUtil;
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
        // 允许一个key有多个限流规则的集合
        List<K> oneKeyMultiplyControlList = frequencyControlList.stream().filter(FrequencyControlDTO::isOneKeyMultiplyControl).toList();
        // 获取 frequencyControlList 和 oneKeyMultiplyControlList 的差集（一个key只能有一个限流规则的集合）
        List<K> oneKeyMultiplyControlListDiff = frequencyControlList.stream().filter(frequencyControl -> !oneKeyMultiplyControlList.contains(frequencyControl)).toList();
        // 去重有只允许一个限流规则的集合
        Map<String, K> frequencyControlDTOMap = oneKeyMultiplyControlListDiff.stream().collect(Collectors.groupingBy(K::getKey, Collectors.collectingAndThen(Collectors.toList(), list -> list.get(0))));
        // 合并
        frequencyControlDTOMap.putAll(oneKeyMultiplyControlList.stream().collect(Collectors.toMap(K::getKey, Function.identity())));

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
