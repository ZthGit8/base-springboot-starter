package com.my.base.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 创建一个线程池调度器
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

        // 设置线程池大小
        threadPoolTaskScheduler.setPoolSize(5);

        // 设置线程名称前缀
        threadPoolTaskScheduler.setThreadNamePrefix("annotation-task-scheduler-");

        // 初始化线程池
        threadPoolTaskScheduler.initialize();
        // 将自定义的线程池设置为调度器
        taskRegistrar.setTaskScheduler(threadPoolTaskScheduler);
    }
}