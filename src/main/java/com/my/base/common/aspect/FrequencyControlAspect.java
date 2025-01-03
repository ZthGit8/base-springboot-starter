package com.my.base.common.aspect;



import cn.hutool.core.util.StrUtil;
import com.my.base.common.annotation.FrequencyControl;
import com.my.base.common.frequencycontrol.strategy.FrequencyControlConstant;
import com.my.base.common.service.frequencycontrol.FrequencyControlInvoke;
import com.my.base.common.frequencycontrol.domain.FixedWindowDTO;
import com.my.base.common.frequencycontrol.domain.SlidingWindowDTO;
import com.my.base.common.frequencycontrol.domain.TokenBucketDTO;
import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;
import com.my.base.common.interceptor.context.RequestContext;
import com.my.base.common.utils.SpElUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 频控实现
 */
@Slf4j
@Aspect
@Component
public class FrequencyControlAspect {

    private static String TOTAL_COUNT_WITH_IN_FIX_TIME = FrequencyControlConstant.TOTAL_COUNT_WITH_IN_FIX_TIME;

    private static String SLIDING_WINDOW = FrequencyControlConstant.SLIDING_WINDOW;

    private static String TOKEN_BUCKET = FrequencyControlConstant.TOKEN_BUCKET;

    @Around("@annotation(com.my.base.common.annotation.FrequencyControl)||@annotation(com.my.base.common.annotation.FrequencyControlContainer)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        FrequencyControl[] annotationsByType = method.getAnnotationsByType(FrequencyControl.class);
        Map<String, FrequencyControl> keyMap = new HashMap<>();
        String strategy = TOTAL_COUNT_WITH_IN_FIX_TIME;
        for (int i = 0; i < annotationsByType.length; i++) {
            // 获取频控注解
            FrequencyControl frequencyControl = annotationsByType[i];
            String prefix = StrUtil.isBlank(frequencyControl.prefixKey()) ? /* 默认方法限定名 + 注解排名（可能多个）*/method.toGenericString() + ":index:" + i : frequencyControl.prefixKey();
            String key = "";
            switch (frequencyControl.target()) {
                case EL:
                    key = SpElUtil.parseSpEl(method, joinPoint.getArgs(), frequencyControl.spEl());
                    break;
                case IP:
                    key = RequestContext.getRequestInfo().getRequestIp();
                    break;
            }
            keyMap.put(prefix + ":" + key, frequencyControl);
            strategy = frequencyControl.strategy();
        }
        // 将注解的参数转换为编程式调用需要的参数
        if (TOTAL_COUNT_WITH_IN_FIX_TIME.equals(strategy)) {
            // 调用编程式注解 固定窗口
            List<FrequencyControlDTO> frequencyControlDTOS = keyMap.entrySet().stream().map(entrySet -> buildFixedWindowDTO(entrySet.getKey(), entrySet.getValue())).collect(Collectors.toList());
            return FrequencyControlInvoke.executeWithFrequencyControlList(strategy, frequencyControlDTOS, joinPoint::proceed);

        } else if (TOKEN_BUCKET.equals(strategy)) {
            // 调用编程式注解 令牌桶
            List<TokenBucketDTO> frequencyControlDTOS = keyMap.entrySet().stream().map(entrySet -> buildTokenBucketDTO(entrySet.getKey(), entrySet.getValue())).collect(Collectors.toList());
            return FrequencyControlInvoke.executeWithFrequencyControlList(strategy, frequencyControlDTOS, joinPoint::proceed);
        } else {
            // 调用编程式注解 滑动窗口
            List<SlidingWindowDTO> frequencyControlDTOS = keyMap.entrySet().stream().map(entrySet -> buildSlidingWindowFrequencyControlDTO(entrySet.getKey(), entrySet.getValue())).collect(Collectors.toList());
            return FrequencyControlInvoke.executeWithFrequencyControlList(strategy, frequencyControlDTOS, joinPoint::proceed);
        }
    }

    /**
     * 将注解参数转换为编程式调用所需要的参数
     *
     * @param key              频率控制Key
     * @param frequencyControl 注解
     * @return 编程式调用所需要的参数-FrequencyControlDTO
     */
    private SlidingWindowDTO buildSlidingWindowFrequencyControlDTO(String key, FrequencyControl frequencyControl) {
        SlidingWindowDTO frequencyControlDTO = new SlidingWindowDTO();
        frequencyControlDTO.setWindowSize(frequencyControl.windowSize());
        frequencyControlDTO.setPeriod(frequencyControl.period());
        frequencyControlDTO.setCount(frequencyControl.count());
        frequencyControlDTO.setUnit(frequencyControl.unit());
        frequencyControlDTO.setKey(key);
        return frequencyControlDTO;
    }

    /**
     * 将注解参数转换为编程式调用所需要的参数
     *
     * @param key              频率控制Key
     * @param frequencyControl 注解
     * @return 编程式调用所需要的参数-FrequencyControlDTO
     */
    private TokenBucketDTO buildTokenBucketDTO(String key, FrequencyControl frequencyControl) {
        TokenBucketDTO tokenBucketDTO = new TokenBucketDTO(frequencyControl.capacity(), frequencyControl.refillRate());
        tokenBucketDTO.setKey(key);
        return tokenBucketDTO;
    }

    /**
     * 将注解参数转换为编程式调用所需要的参数
     *
     * @param key              频率控制Key
     * @param frequencyControl 注解
     * @return 编程式调用所需要的参数-FrequencyControlDTO
     */
    private FixedWindowDTO buildFixedWindowDTO(String key, FrequencyControl frequencyControl) {
        FixedWindowDTO fixedWindowDTO = new FixedWindowDTO();
        fixedWindowDTO.setCount(frequencyControl.count());
        fixedWindowDTO.setTime(frequencyControl.time());
        fixedWindowDTO.setUnit(frequencyControl.unit());
        fixedWindowDTO.setKey(key);
        fixedWindowDTO.setOneKeyMultiplyControl(frequencyControl.isOneKeyMultiplyControl());
        return fixedWindowDTO;
    }
}
