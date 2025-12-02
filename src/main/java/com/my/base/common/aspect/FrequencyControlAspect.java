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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频控实现
 */
@Slf4j
@Aspect
@Component
public class FrequencyControlAspect {

    // 缓存方法的注解信息
    private final Map<Method, FrequencyControl[]> annotationCache = new ConcurrentHashMap<>();

    // 缓存前缀信息
    private final Map<Method, List<String>> prefixCache = new ConcurrentHashMap<>();

    // 缓存DTO模板
    private final Map<String, FrequencyControlDTO> dtoTemplateCache = new ConcurrentHashMap<>();

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
        // 获取方法上的所有FrequencyControl注解（带缓存）
        FrequencyControl[] annotations = annotationCache.computeIfAbsent(method,
            m -> m.getAnnotationsByType(FrequencyControl.class));

        // 获取策略类型(所有注解使用相同策略)
        String strategy = annotations[0].strategy();

        // 构建频控DTO列表
        List<FrequencyControlDTO> frequencyControlDTOList = buildFrequencyControlDTOs(method, joinPoint, annotations);

        // 执行频控并返回结果
        return FrequencyControlInvoke.executeWithList(strategy, frequencyControlDTOList, joinPoint::proceed);
    }

    /**
     * 构建频控DTO列表
     * @param method 方法
     * @param joinPoint 切点
     * @param annotations 注解数组
     * @return 频控DTO列表
     */
    private List<FrequencyControlDTO> buildFrequencyControlDTOs(Method method, ProceedingJoinPoint joinPoint, FrequencyControl[] annotations) {
        // 获取前缀列表（带缓存）
        List<String> prefixes = prefixCache.computeIfAbsent(method, m -> {
            List<String> prefixList = new ArrayList<>();
            for (FrequencyControl annotation : annotations) {
                String prefix = StrUtil.isBlank(annotation.prefixKey()) ?
                    m.toGenericString() + ":index:" + Arrays.asList(annotations).indexOf(annotation) :
                    annotation.prefixKey();
                prefixList.add(prefix);
            }
            return prefixList;
        });

        List<FrequencyControlDTO> result = new ArrayList<>();
        for (int i = 0; i < annotations.length; i++) {
            FrequencyControl annotation = annotations[i];
            // 获取key
            String key = getKey(method, joinPoint, annotation);
            // 构建并返回DTO（复用模板）
            FrequencyControlDTO dto = buildDTOFromTemplate(prefixes.get(i) + ":" + key, annotation);
            result.add(dto);
        }
        return result;
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
     * 根据注解构建频控DTO（使用模板模式）
     * @param key 频控key
     * @param annotation 注解
     * @return 频控DTO
     */
    private FrequencyControlDTO buildDTOFromTemplate(String key, FrequencyControl annotation) {
        // 生成缓存键
        String cacheKey = generateCacheKey(annotation);

        // 从缓存中获取模板并克隆
        FrequencyControlDTO template = dtoTemplateCache.computeIfAbsent(cacheKey, k -> createDTOTemplate(annotation));

        // 设置具体key
        template.setKey(key);
        return template;
    }

    /**
     * 生成DTO模板的缓存键
     * @param annotation
     * @return
     */
    private String generateCacheKey(FrequencyControl annotation) {
        return annotation.strategy() + ":" +
               annotation.count() + ":" +
               annotation.time() + ":" +
               annotation.unit() + ":" +
               annotation.capacity() + ":" +
               annotation.refillRate() + ":" +
               annotation.windowSize() + ":" +
               annotation.period();
    }

    /**
     * 创建DTO模板
     * @param annotation 注解
     * @return DTO模板
     */
    private FrequencyControlDTO createDTOTemplate(FrequencyControl annotation) {
        return switch (annotation.strategy()) {
            case FrequencyControlConstant.TOTAL_COUNT_WITH_IN_FIX_TIME -> {
                // 构建固定窗口DTO
                FixedWindowDTO fixedDto = new FixedWindowDTO();
                fixedDto.setCount(annotation.count());
                fixedDto.setTime(annotation.time());
                fixedDto.setUnit(annotation.unit());
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
    }
}
