package com.my.base.config;

import com.my.base.config.properties.BaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPollConfiguration implements AsyncConfigurer {
    @Autowired
    private BaseProperties baseProperties;

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 5;
    private static final int KEEP_ALIVE_SECONDS = 10;
    private static final int QUEUE_CAPACITY = 100;

    @Override
    public Executor getAsyncExecutor() {
        return getThreadPoolExecutor();
    }

    @Bean(name = "commonThreadPoolExecutor")
    public ThreadPoolExecutor getThreadPoolExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(CORE_POOL_SIZE);
        threadPoolTaskExecutor.setMaxPoolSize(MAX_POOL_SIZE);
        threadPoolTaskExecutor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        threadPoolTaskExecutor.setQueueCapacity(QUEUE_CAPACITY);
        threadPoolTaskExecutor.setThreadNamePrefix(baseProperties.getThreadPrefix());
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setThreadFactory(new CustomizedThreadFactory(threadPoolTaskExecutor));
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor.getThreadPoolExecutor();
    }

    static class CustomizedThreadFactory implements ThreadFactory {
        private final ThreadFactory factory;

        CustomizedThreadFactory(ThreadFactory factory) {
            this.factory = factory;
        }


        @Override
        public Thread newThread(Runnable r) {
            Thread thread = factory.newThread(r);
            thread.setUncaughtExceptionHandler(GlobalUncaughtExceptionHandler.getInstance());
            return thread;
        }
    }
    @Slf4j
    static class GlobalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        private static final GlobalUncaughtExceptionHandler globalUncaughtExceptionHandler = new GlobalUncaughtExceptionHandler();

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("uncaughtException: exception in {} {} {[]} ",t.getThreadGroup(),t.getName(),e);
        }

        private static GlobalUncaughtExceptionHandler getInstance() {

            return globalUncaughtExceptionHandler;
        }
    }
}
