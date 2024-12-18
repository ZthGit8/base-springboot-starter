package com.my.base.common.service.frequencycontrol;


import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;
import com.my.base.common.frequencycontrol.strategy.FrequencyControl;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流策略工厂
 */
@Component
public class FrequencyControlStrategyFactory implements InitializingBean {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 限流策略集合
     */
    static Map<String, FrequencyControl<?>> frequencyControlServiceStrategyMap = new ConcurrentHashMap<>(8);

    /**
     * 将策略类放入工厂
     *
     * @param strategyName                    策略名称
     * @param frequencyControl 策略类
     */
    public static <K extends FrequencyControlDTO> void registerFrequencyController(String strategyName, FrequencyControl<K> frequencyControl) {
        frequencyControlServiceStrategyMap.put(strategyName, frequencyControl);
    }

    /**
     * 根据名称获取策略类
     *
     * @param strategyName 策略名称
     * @return 对应的限流策略类
     */
    @SuppressWarnings("unchecked")
    public static <K extends FrequencyControlDTO> FrequencyControl<K> getFrequencyControllerByName(String strategyName) {
        return (FrequencyControl<K>) frequencyControlServiceStrategyMap.get(strategyName);
    }

    /**
     * 构造器私有
     */
    private FrequencyControlStrategyFactory() {

    }

    @Override
    public void afterPropertiesSet() {
        Map<String, ? extends AbstractFrequencyControlService> beansOfType = applicationContext.getBeansOfType(AbstractFrequencyControlService.class,true,false);
        beansOfType.values().forEach(service -> {
            try {
                FrequencyControlStrategyFactory.registerFrequencyController(service.getStrategyName(), service);
            } catch (Exception e) {
                throw new RuntimeException("Failed to register frequency controller: " + service.getStrategyName() + e);
            }
        });
    }
}
