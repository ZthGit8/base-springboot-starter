package com.my.base.common.service.frequencycontrol;

import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;
import com.my.base.common.frequencycontrol.strategy.FrequencyControl;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流策略工厂
 */
@Component
public class FrequencyControlStrategyFactory implements InitializingBean {
    private final ApplicationContext applicationContext;
    private static final Map<String, FrequencyControl<?>> STRATEGY_MAP = new ConcurrentHashMap<>(8);

    public FrequencyControlStrategyFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 根据名称获取策略类
     *
     * @param strategyName 策略名称
     * @return 对应的限流策略类
     */
    @SuppressWarnings("unchecked")
    public static <K extends FrequencyControlDTO> FrequencyControl<K> getStrategy(String strategyName) {
        return (FrequencyControl<K>) STRATEGY_MAP.get(strategyName);
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, AbstractFrequencyControlService> services = 
            applicationContext.getBeansOfType(AbstractFrequencyControlService.class);
        
        services.values().forEach(service -> 
            STRATEGY_MAP.put(service.getStrategyName(), service));
    }
}
