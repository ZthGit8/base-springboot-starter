package com.my.base.config.property;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 绑定配置基础类
 */
@Data
@Configuration
@ConfigurationProperties("spring.rabbitmq")
public class RabbitModuleProperties {

    /**
     * 模块配置
     */
    List<ModuleProperties> modules;

}
