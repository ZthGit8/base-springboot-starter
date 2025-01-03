package com.my.base.config.property;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "my.base")
public class BaseProperties {
    /**
     * 线程池前缀
     */
    private String threadPrefix;
    /**
     * 是否打印或存储请求响应日志
     */
    private boolean requestLogEnable;
    /**
     * 是否存储慢Sql日志
     */
    private boolean slowSqlLogEnable;
    /**
     * 是否打印或存储Sql日志
     */
    private boolean sqlLogEnable;
    /**
     * 使用那种敏感词过滤策略 默认使用DFA，可以使用AC
     */
    private String useSensitiveWordType = "DFA";
    /**
     * 线程池核心线程数
     */
    private int corePoolSize = 5;
    /**
     * 线程池最大线程数
     */
    private int maxPoolSize = 10;
    /**
     * 线程池线程空闲时间
     */
    private int keepAliveSeconds = 10;
    /**
     * 线程池队列大小
     */
    private int queueCapacity = 100;

}
