package com.my.base.common.aspect;


import cn.hutool.core.util.StrUtil;
import com.my.base.common.annotation.FrequencyControl;
import com.my.base.common.frequencycontrol.strategy.FrequencyControlConstant;
import com.my.base.common.service.frequencycontrol.FrequencyControlInvoke;
import com.my.base.common.frequencycontrol.domain.FixedWindowDTO;
import com.my.base.common.frequencycontrol.domain.SlidingWindowDTO;
import com.my.base.common.frequencycontrol.domain.TokenBucketDTO;
import com.my.base.common.frequencycontrol.domain.FrequencyControlDTO;
import com.my.base.common.context.RequestContext;
import com.my.base.common.utils.SpElUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 频控实现
 */
@Slf4j
@Aspect
@Component
public class FrequencyControlAspect {


    @Around("@annotation(com.my.base.common.annotation.FrequencyControl) || @annotation(com.my.base.common.annotation.FrequencyControlContainer)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        FrequencyControl[] annotations = method.getAnnotationsByType(FrequencyControl.class);
        
        String strategy = annotations[0].strategy(); // 所有注解使用相同策略
        List<FrequencyControlDTO> dtos = buildFrequencyControlDTOs(method, joinPoint, annotations);
        
        return FrequencyControlInvoke.executeWithFrequencyControlList(strategy, dtos, joinPoint::proceed);
    }

    private List<FrequencyControlDTO> buildFrequencyControlDTOs(Method method, ProceedingJoinPoint joinPoint, FrequencyControl[] annotations) {
        return Arrays.stream(annotations)
            .map(annotation -> {
                String prefix = getPrefix(method, annotation);
                String key = getKey(method, joinPoint, annotation);
                return buildDTO(prefix + ":" + key, annotation);
            })
            .collect(Collectors.toList());
    }

    private String getPrefix(Method method, FrequencyControl annotation) {
        return StrUtil.isBlank(annotation.prefixKey()) ? 
            method.toGenericString() + ":index:" + Arrays.asList(method.getAnnotationsByType(FrequencyControl.class)).indexOf(annotation) :
            annotation.prefixKey();
    }

    private String getKey(Method method, ProceedingJoinPoint joinPoint, FrequencyControl annotation) {
        return switch (annotation.target()) {
            case EL -> {
                try {
                    yield SpElUtil.parseSpEl(method, joinPoint.getArgs(), annotation.spEl());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to parse SpEl expression", e);
                }
            }
            case IP -> RequestContext.getRequestInfo().getRequestIp();
            default -> "";
        };
    }

    private FrequencyControlDTO buildDTO(String key, FrequencyControl annotation) {
        FrequencyControlDTO dto = switch (annotation.strategy()) {
            case FrequencyControlConstant.TOTAL_COUNT_WITH_IN_FIX_TIME -> {
                FixedWindowDTO fixedDto = new FixedWindowDTO();
                fixedDto.setCount(annotation.count());
                fixedDto.setTime(annotation.time());
                fixedDto.setUnit(annotation.unit());
                fixedDto.setOneKeyMultiplyControl(annotation.isOneKeyMultiplyControl());
                yield fixedDto;
            }
            case FrequencyControlConstant.TOKEN_BUCKET -> new TokenBucketDTO(annotation.capacity(), annotation.refillRate());
            case FrequencyControlConstant.SLIDING_WINDOW -> {
                SlidingWindowDTO slidingDto = new SlidingWindowDTO();
                slidingDto.setWindowSize(annotation.windowSize());
                slidingDto.setPeriod(annotation.period());
                slidingDto.setCount(annotation.count());
                slidingDto.setUnit(annotation.unit());
                yield slidingDto;
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + annotation.strategy());
        };
        
        dto.setKey(key);
        return dto;
    }
}
