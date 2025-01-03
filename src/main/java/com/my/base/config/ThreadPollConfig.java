package com.my.base.config;

import com.my.base.common.interceptor.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import com.my.base.config.property.BaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@ConditionalOnBean(BaseProperties.class)
public class ThreadPollConfig implements AsyncConfigurer {
    @Autowired
    private BaseProperties baseProperties;

    @Override
    public Executor getAsyncExecutor() {
        return getThreadPoolExecutor();
    }

    @Bean(name = "commonThreadPoolExecutor")
    public ThreadPoolExecutor getThreadPoolExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(baseProperties.getCorePoolSize());
        threadPoolTaskExecutor.setMaxPoolSize(baseProperties.getMaxPoolSize());
        threadPoolTaskExecutor.setKeepAliveSeconds(baseProperties.getKeepAliveSeconds());
        threadPoolTaskExecutor.setQueueCapacity(baseProperties.getQueueCapacity());
        threadPoolTaskExecutor.setThreadNamePrefix(baseProperties.getThreadPrefix());
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setThreadFactory(new CustomizedThreadFactory(threadPoolTaskExecutor));
        threadPoolTaskExecutor.setTaskDecorator(new ContextTaskDecorator());
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor.getThreadPoolExecutor();
    }

    /**
     * 自定义线程工厂，捕获子线程异常
     */
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

    /**
     * 子线程异常捕获
     */
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

    /**
     * 线程池装饰器，将线程池中线程的requestInfo信息传递到子线程中
     */
    public static class ContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            RequestInfo requestInfo = RequestContext.getRequestInfo();
            return () -> {
                try {
                    RequestContext.setRequestInfo(requestInfo);
                    runnable.run();
                } finally {
                    RequestContext.remove();
                }
            };
        }
    }
}
