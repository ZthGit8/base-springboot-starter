package com.my.base.common.demo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器+信号量隔离demo
 */
public class CircuitBreakerWithIsolation {
    // 信号量隔离
    private Semaphore semaphore = new Semaphore(10);
    
    // 熔断器
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private long lastFailureTime = 0;
    private volatile boolean circuitOpen = false;
    private final int failureThreshold = 5;  // 失败阈值
    private final long timeout = 60000;      // 熔断超时时间(1分钟)

    public String callService() {
        // 1. 隔离策略：限制并发访问
        if (!semaphore.tryAcquire()) {
            // 隔离生效，快速失败
            return handleIsolationFallback();
        }
        
        try {
            // 2. 检查熔断器状态
            if (isCircuitOpen()) {
                // 熔断器打开，直接降级
                return handleCircuitBreakerFallback();
            }
            
            // 3. 执行实际业务逻辑
            String result = doRealServiceCall();
            
            // 4. 成功调用，重置熔断器
            resetCircuitBreaker();
            
            return result;
        } catch (Exception e) {
            // 5. 失败处理，记录失败并可能触发熔断
            recordFailure();
            return handleServiceFallback();
        } finally {
            // 6. 释放信号量许可
            semaphore.release();
        }
    }
    
    private boolean isCircuitOpen() {
        if (circuitOpen) {
            // 检查是否应该半开（尝试恢复）
            if (System.currentTimeMillis() - lastFailureTime > timeout) {
                circuitOpen = false; // 半开状态
                failureCount.set(0);
            }
        }
        return circuitOpen;
    }
    
    private void recordFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        
        // 达到失败阈值，打开熔断器
        if (failures >= failureThreshold) {
            circuitOpen = true;
        }
    }
    
    private void resetCircuitBreaker() {
        failureCount.set(0);
        circuitOpen = false;
    }
    
    private String doRealServiceCall() throws Exception {
        // 模拟实际服务调用
        if (Math.random() < 0.3) { // 30%概率失败
            throw new RuntimeException("服务调用失败");
        }
        Thread.sleep(500);
        return "服务调用成功";
    }
    
    private String handleIsolationFallback() {
        return "系统繁忙，请稍后再试（隔离限制）";
    }
    
    private String handleCircuitBreakerFallback() {
        return "服务暂时不可用（熔断中）";
    }
    
    private String handleServiceFallback() {
        return "服务暂时不可用，使用默认数据";
    }
}
