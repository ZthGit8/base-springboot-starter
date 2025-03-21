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


    /**
     * 环绕通知,处理带有FrequencyControl注解的方法
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("@annotation(com.my.base.common.annotation.FrequencyControl) || @annotation(com.my.base.common.annotation.FrequencyControlContainer)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前方法
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 获取方法上的所有FrequencyControl注解
        FrequencyControl[] annotations = method.getAnnotationsByType(FrequencyControl.class);
        
        // 获取策略类型(所有注解使用相同策略)
        String strategy = annotations[0].strategy(); 
        // 构建频控DTO列表
        List<FrequencyControlDTO> dtos = buildFrequencyControlDTOs(method, joinPoint, annotations);
        
        // 执行频控并返回结果
        return FrequencyControlInvoke.executeWithList(strategy, dtos, joinPoint::proceed);
    }

    /**
     * 构建频控DTO列表
     * @param method 方法
     * @param joinPoint 切点
     * @param annotations 注解数组
     * @return 频控DTO列表
     */
    private List<FrequencyControlDTO> buildFrequencyControlDTOs(Method method, ProceedingJoinPoint joinPoint, FrequencyControl[] annotations) {
        return Arrays.stream(annotations)
            .map(annotation -> {
                // 获取前缀
                String prefix = getPrefix(method, annotation);
                // 获取key
                String key = getKey(method, joinPoint, annotation);
                // 构建并返回DTO
                return buildDTO(prefix + ":" + key, annotation);
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取频控前缀
     * @param method 方法
     * @param annotation 注解
     * @return 前缀
     */
    private String getPrefix(Method method, FrequencyControl annotation) {
        // 如果注解未指定前缀,则使用方法签名+索引作为前缀
        return StrUtil.isBlank(annotation.prefixKey()) ? 
            method.toGenericString() + ":index:" + Arrays.asList(method.getAnnotationsByType(FrequencyControl.class)).indexOf(annotation) :
            annotation.prefixKey();
    }

    /**
     * 获取频控key
     * @param method 方法
     * @param joinPoint 切点
     * @param annotation 注解
     * @return key
     */
    private String getKey(Method method, ProceedingJoinPoint joinPoint, FrequencyControl annotation) {
        return switch (annotation.target()) {
            case EL -> {
                try {
                    // 解析SpEl表达式
                    yield SpElUtil.parseSpEl(method, joinPoint.getArgs(), annotation.spEl());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to parse SpEl expression", e);
                }
            }
            case IP -> RequestContext.getRequestInfo().getRequestIp(); // 获取请求IP
            default -> "";
        };
    }

    /**
     * 根据注解构建频控DTO
     * @param key 频控key
     * @param annotation 注解
     * @return 频控DTO
     */
    private FrequencyControlDTO buildDTO(String key, FrequencyControl annotation) {
        FrequencyControlDTO dto = switch (annotation.strategy()) {
            case FrequencyControlConstant.TOTAL_COUNT_WITH_IN_FIX_TIME -> {
                // 构建固定窗口DTO
                FixedWindowDTO fixedDto = new FixedWindowDTO();
                fixedDto.setCount(annotation.count());
                fixedDto.setTime(annotation.time());
                fixedDto.setUnit(annotation.unit());
                fixedDto.setOneKeyMultiplyControl(annotation.isOneKeyMultiplyControl());
                yield fixedDto;
            }
            case FrequencyControlConstant.TOKEN_BUCKET -> 
                // 构建令牌桶DTO
                new TokenBucketDTO(annotation.capacity(), annotation.refillRate());
            case FrequencyControlConstant.SLIDING_WINDOW -> {
                // 构建滑动窗口DTO
                SlidingWindowDTO slidingDto = new SlidingWindowDTO();
                slidingDto.setWindowSize(annotation.windowSize());
                slidingDto.setPeriod(annotation.period());
                slidingDto.setCount(annotation.count());
                slidingDto.setUnit(annotation.unit());
                yield slidingDto;
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + annotation.strategy());
        };
        
        // 设置key并返回
        dto.setKey(key);
        return dto;
    }
}
