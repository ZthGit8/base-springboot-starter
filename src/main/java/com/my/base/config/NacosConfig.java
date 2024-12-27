package com.my.base.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

/**
 * nacos配置
 */
@Slf4j
@Configuration
public class NacosConfig {

    @Value("${spring.cloud.nacos.server-addr}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.discovery.namespace}")
    private String namespace;

    @Bean
    @Primary
    public ConfigService configService() {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);
        try {
            return NacosFactory.createConfigService(properties);
        } catch (NacosException e) {
            log.error("configService 配置失败 {}", e.getMessage());
        }
        return null;
    }
}