package com.my.base.common.task;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * @author
 * @date 2025/3/12 17:21
 * @description: 自定义的定时任务
 */
public class ScheduledTask {

    private static class SingletonHolder {
        private static final ScheduledTask INSTANCE = new ScheduledTask();

        static {
            try {
                ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
                threadPoolTaskScheduler.setPoolSize(5);
                threadPoolTaskScheduler.setThreadNamePrefix("customize-task-scheduler-");
                threadPoolTaskScheduler.initialize();
                INSTANCE.taskScheduler = threadPoolTaskScheduler;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize ThreadPoolTaskScheduler", e);
            }
        }
    }

    public TaskScheduler taskScheduler;

    private ScheduledTask() {}

    public static ScheduledTask getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static ScheduledFuture<?> execute(Runnable runnable, int delay, long period) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        return getInstance().taskScheduler.scheduleAtFixedRate(runnable,
                Date.from(DateUtils.addMilliseconds(Date.from(Instant.now()), delay).toInstant()).toInstant(),
                Duration.ofMillis(period));
    }

    public static ScheduledFuture<?> execute(Runnable runnable, Trigger trigger) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        return getInstance().taskScheduler.schedule(runnable, trigger);
    }
}
