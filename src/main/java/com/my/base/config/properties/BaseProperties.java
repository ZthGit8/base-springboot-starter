package com.my.base.config.properties;

import lombok.Data;
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

}
