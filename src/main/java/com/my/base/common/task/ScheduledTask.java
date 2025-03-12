package com.my.base.common.task;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;


/**
 * @author
 * @date 2025/3/12 17:21
 * @description:
 */
public class ScheduledTask {

    public static ScheduledTask scheduledTask = null;

    public TaskScheduler taskScheduler;

    private ScheduledTask(ThreadPoolTaskScheduler threadPoolTaskScheduler) {
        taskScheduler = threadPoolTaskScheduler;
    }

    public static ScheduledTask getInstance() {
        if (scheduledTask == null) {
            // 创建一个线程池调度器
            ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

            // 设置线程池大小
            threadPoolTaskScheduler.setPoolSize(5);

            // 设置线程名称前缀
            threadPoolTaskScheduler.setThreadNamePrefix("customize-task-scheduler-");

            // 初始化线程池
            threadPoolTaskScheduler.initialize();
            scheduledTask = new ScheduledTask(threadPoolTaskScheduler);
        }
        return scheduledTask;
    }

    public static ScheduledFuture<?> submit(Runnable runnable, int delay, long period) {
        return getInstance().taskScheduler.scheduleAtFixedRate(runnable,
                DateUtils.addMilliseconds(new Date(), delay).toInstant(),
                Duration.ofMillis(period));
    }
}
