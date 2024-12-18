package com.my.base.common.aspect;

import cn.hutool.core.util.StrUtil;
import com.my.base.common.annotation.DistributionLock;
import com.my.base.common.service.lock.LockService;
import com.my.base.common.utils.SpElUtils;
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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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

    @Around("@annotation(com.my.base.common.annotation.DistributionLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DistributionLock distributionLock = method.getAnnotation(DistributionLock.class);
        String prefix = StrUtil.isBlank(distributionLock.prefixKey()) ? SpElUtils.getMethodKey(method) : distributionLock.prefixKey();//默认方法限定名+注解排名（可能多个）
        String key = SpElUtils.parseSpEl(method, joinPoint.getArgs(), distributionLock.key());

        LockService lockService = lockServiceMap.get(distributionLock.useLockType());

        return lockService.executeWithLockThrows(prefix + ":" + key, distributionLock.waitTime(), distributionLock.unit(), joinPoint::proceed);
    }
}
