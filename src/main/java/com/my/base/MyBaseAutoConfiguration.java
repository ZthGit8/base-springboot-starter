package com.my.base;

import com.my.base.common.sensitive.*;
import com.my.base.config.property.BaseProperties;
import feign.Request;
import feign.Retryer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableConfigurationProperties(BaseProperties.class)
public class MyBaseAutoConfiguration {

    @Autowired
    private RedisProperties redisProperties;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setPassword(redisProperties.getPassword())
                .setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
    @Bean
    public SensitiveWordBs sensitiveWordBs(BaseProperties properties) {
        SensitiveWordFilter instance = DFAFilter.getInstance();
        if (properties.getUseSensitiveWordType().equals("AC")) {
            instance = ACFilter.getInstance();
        }
        return SensitiveWordBs.newInstance()
                .filterStrategy(instance)
                .init();
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(30000,60000);
    }

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

}
