package com.my.base.common.aspect;

import cn.hutool.core.util.ReflectUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Description: 分布式锁切面
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-04-20
 */
@Slf4j
@Aspect
@Component
@Order(0)//确保比事务注解先执行，分布式锁在事务外
public class DistributionLockAspect {
    @Autowired
    private ApplicationContext applicationContext;

    Map<String, LockService> lockServiceMap = new HashMap<>();

    @PostConstruct
    public void init() {
        applicationContext.getBeansOfType(LockService.class).forEach(
                (name, service) -> lockServiceMap.put(service.getLockType(), service)
        );
    }

    // 环绕通知，用于处理带有DistributionLock注解的方法
    @Around("@annotation(com.my.base.common.annotation.DistributionLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前方法
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 获取方法上的DistributionLock注解
        DistributionLock distributionLock = method.getAnnotation(DistributionLock.class);

        // 确定锁的前缀键，如果注解中未指定，则使用SpEl表达式生成的方法键
        String prefix = StrUtil.isBlank(distributionLock.prefixKey()) ? SpElUtil.getMethodKey(method) : distributionLock.prefixKey();

        // 获取锁的键
        String key = getLockKey(joinPoint, method);

        // 根据注解中指定的锁类型获取对应的LockService实现
        LockService lockService = lockServiceMap.get(distributionLock.useLockType());

        // 使用分布式锁执行方法，并返回结果
        return lockService.executeWithLockThrows(prefix + ":" + key, distributionLock.waitTime(), distributionLock.unit(), joinPoint::proceed);
    }

    private String getLockKey(ProceedingJoinPoint joinPoint, Method method) {
        Object[] args = joinPoint.getArgs();
        List<String> keyParts = new ArrayList<>();
        if (hasLineFieldLock(method)) {
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
        } else if (hasBeanFieldLock(method)) {

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

            return method.getAnnotation(DistributionLock.class).key();
        }
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
     * 哈希敏感数据的方法
     *
     * @param data
     * @return
     */
    private String hashSensitiveData(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
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

}
