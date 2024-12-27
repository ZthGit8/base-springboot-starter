package com.my.base;

import com.my.base.common.sensitive.*;
import com.my.base.config.property.BaseProperties;
import com.my.base.config.property.RabbitModuleProperties;
import feign.Request;
import feign.Retryer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


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

    /**
     * 创建连接工厂，并启用发布确认
     * @return
     */
    @Bean
    @Primary
    @ConditionalOnBean(RabbitProperties.class)
    public ConnectionFactory connectionFactory(RabbitProperties rabbitProperties) {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitProperties.getHost()+":"+rabbitProperties.getPort());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); // 启用发布确认
        connectionFactory.setPublisherReturns(true); // 启用返回确认模式
        return connectionFactory;
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
