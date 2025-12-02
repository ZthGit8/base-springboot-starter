package com.my.base.common.aspect;

import cn.hutool.core.util.StrUtil;
import com.my.base.common.annotation.DistributionLock;
import com.my.base.common.annotation.LockField;
import com.my.base.common.service.lock.LockService;
import com.my.base.common.utils.SpElUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Description: 分布式锁切面
 */
@Slf4j
@Aspect
@Component
@Order(0)//确保比事务注解先执行，分布式锁在事务外
public class DistributionLockAspect {
    private final ApplicationContext applicationContext;

    Map<String, LockService> lockServiceMap = new HashMap<>();

    // 添加缓存：方法元数据缓存
    private final Map<Method, MethodMetadata> methodMetadataCache = new ConcurrentHashMap<>();

    // 添加缓存：Hash值缓存
    private final Map<String, String> hashCache = new ConcurrentHashMap<>();

    public DistributionLockAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        applicationContext.getBeansOfType(LockService.class).forEach(
                (name, service) -> lockServiceMap.put(service.getLockType(), service)
        );
    }

    // 环环通知，用于处理带有DistributionLock注解的方法
    @Around("@annotation(com.my.base.common.annotation.DistributionLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前方法
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 获取方法上的DistributionLock注解
        DistributionLock distributionLock = method.getAnnotation(DistributionLock.class);

        // 参数校验
        if (distributionLock == null) {
            throw new IllegalStateException("DistributionLock annotation not found");
        }

        // 从缓存中获取或创建方法元数据
        MethodMetadata metadata = methodMetadataCache.computeIfAbsent(method, this::createMethodMetadata);

        // 确定锁的前缀键，如果注解中未指定，则使用SpEl表达式生成的方法键
        String prefix = StrUtil.isBlank(distributionLock.prefixKey()) ? SpElUtil.getMethodKey(method) : distributionLock.prefixKey();

        // 获取锁的键
        String key = getLockKey(joinPoint, method, metadata);

        // 根据注解中指定的锁类型获取对应的LockService实现
        LockService lockService = Optional.ofNullable(lockServiceMap.get(distributionLock.useLockType()))
                .orElseThrow(() -> new IllegalStateException("LockService not found for type: " + distributionLock.useLockType()));

        // 构建完整的锁键
        String lockKey = prefix + ":" + key;
        log.debug("Acquiring distributed lock with key: {}", lockKey);

        try {
            // 使用分布式锁执行方法，并返回结果
            return lockService.executeWithLockThrows(lockKey, distributionLock.waitTime(), distributionLock.unit(), joinPoint::proceed);
        } catch (Throwable e) {
            log.error("Error executing method with distributed lock. Key: {}, Error: {}", lockKey, e.getMessage());
            throw e;
        }
    }

    private String getLockKey(ProceedingJoinPoint joinPoint, Method method, MethodMetadata metadata) {
        Object[] args = joinPoint.getArgs();
        List<String> keyParts = new ArrayList<>();
        if (metadata.hasLineFieldLock) {
            // 获取参数注解
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof LockField) {
                        // 安全性改进：对参数进行哈希处理
                        String paramValue = Objects.toString(args[i], "");
                        keyParts.add(hashSensitiveData(paramValue));
                    }
                }
            }

            // 如果没有找到带 LockField 注解的参数，返回默认值或抛出异常
            if (keyParts.isEmpty()) {
                throw new IllegalStateException("No parameters annotated with @LockField found");
            }

            // 使用 String.join 简化字符串拼接
            return String.join(":", keyParts);
        } else if (metadata.hasBeanFieldLock) {

            Class<?>[] parameterTypes = method.getParameterTypes();

            for (int i = 0; i < parameterTypes.length; i++) {
                Field[] fields = parameterTypes[i].getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(LockField.class)) {
                        field.setAccessible(true);
                        try {
                            Object fieldValue = field.get(args[i]);
                            String paramValue = Objects.toString(fieldValue, "");
                            keyParts.add(hashSensitiveData(paramValue));
                        } catch (IllegalAccessException e) {
                            log.error("Error accessing field: {}", field.getName(), e);
                        }
                    }
                }
            }

            // 使用 String.join 简化字符串拼接
            return String.join(":", keyParts);
        } else {
            try {
                return SpElUtil.parseSpEl(method, joinPoint.getArgs(), method.getAnnotation(DistributionLock.class).key());
            } catch (IllegalAccessException e) {
                log.error("Error parsing SpEl expression: {}", e.getMessage());
                // 直接抛出异常
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 创建方法元数据并缓存
     * @param method
     * @return
     */
    private MethodMetadata createMethodMetadata(Method method) {
        MethodMetadata metadata = new MethodMetadata();
        metadata.hasLineFieldLock = hasLineFieldLock(method);
        metadata.hasBeanFieldLock = hasBeanFieldLock(method);
        return metadata;
    }

    /**
     * 判断方法参数是否包含LockField注解
     *
     * @param method
     * @return
     */
    private static boolean hasLineFieldLock(Method method) {
        return Arrays.stream(Arrays.stream(method.getParameterAnnotations())
                .flatMap(Arrays::stream)
                .toArray(Object[]::new)).anyMatch(annotation -> annotation instanceof LockField);
    }

    /**
     * 判断方法参数的实体类内是否包含FieldLock注解
     *
     * @param method
     * @return
     */
    private static boolean hasBeanFieldLock(Method method) {
        // 获取方法参数和参数类型
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
            Field[] fields = parameterType.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(LockField.class)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 哈希敏感数据的方法，增加缓存机制
     *
     * @param data
     * @return
     */
    private String hashSensitiveData(String data) {
        // 先尝试从缓存中获取
        return hashCache.computeIfAbsent(data, this::computeHash);
    }

    /**
     * 计算哈希值的实际方法
     * @param data
     * @return
     */
    private String computeHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing sensitive data", e);
        }
    }

    /**
     * 方法元数据类，用于缓存方法相关信息
     */
    private static class MethodMetadata {
        boolean hasLineFieldLock;
        boolean hasBeanFieldLock;
    }

}
