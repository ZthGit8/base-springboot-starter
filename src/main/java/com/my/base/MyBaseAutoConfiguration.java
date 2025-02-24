package com.my.base;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.my.base.common.interceptor.MybatisPlusAllSqlLog;
import com.my.base.common.sensitive.ACFilter;
import com.my.base.common.sensitive.DFAFilter;
import com.my.base.common.sensitive.SensitiveWordBs;
import com.my.base.common.sensitive.SensitiveWordFilter;
import com.my.base.config.property.BaseProperties;
import feign.Request;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;


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
     *
     * @return
     */
    @Bean
    @Primary
    @ConditionalOnBean(RabbitProperties.class)
    public ConnectionFactory connectionFactory(RabbitProperties rabbitProperties) {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitProperties.getHost() + ":" + rabbitProperties.getPort());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); // 启用发布确认
        connectionFactory.setPublisherReturns(true); // 启用返回确认模式
        return connectionFactory;
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(30000, 60000);
    }

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    /**
     * 配置redisTemplate
     *
     * @param factory
     * @return
     */
    @Primary
    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Jackson2JsonRedisSerializer<?> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        // key采用String的序列化方式
        template.setKeySerializer(stringRedisSerializer);
        // hash的key也采用String的序列化方式
        template.setHashKeySerializer(stringRedisSerializer);
        // value序列化方式采用jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 给事件监听器添加线程池，避免事件监听器阻塞主线程
     *
     * @param taskExecutor
     * @return
     */
    @Bean
    @ConditionalOnBean(ThreadPoolExecutor.class)
    public ApplicationEventMulticaster applicationEventMulticaster(ThreadPoolExecutor taskExecutor) {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(taskExecutor);
        return eventMulticaster;
    }

    /**
     * 默认解析器 其中locale表示默认语言
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolverConfig();
    }

    private static class LocaleResolverConfig implements LocaleResolver {

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            // 获取请求来的语言方式
            String language = request.getHeader("lang");
            // 获取请求头默认的local对象
            Locale locale = request.getLocale();
            if (StringUtils.isNoneBlank(language)) {
                // 按照指定的正则表达式，解析出相关的数据信息
                String[] split = language.split("_");
                // 解析出数据后，修改local对象
                if (split.length == 2)
                    locale = Locale.of(split[0], split[1]);
            }
            return locale;
        }

        @Override
        public void setLocale(@Nullable HttpServletRequest request, HttpServletResponse response, Locale locale) {

        }
    }

//    @Bean
//    @ConditionalOnBean({MybatisPlusAllSqlLog.class})
//    public MybatisPlusInterceptor mybatisPlusInterceptor(MybatisPlusAllSqlLog mybatisPlusAllSqlLog) {
//        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
//        mybatisPlusInterceptor.addInnerInterceptor(mybatisPlusAllSqlLog);
//        return mybatisPlusInterceptor;
//    }
//
//    @Bean
//    public MybatisPlusInterceptor paginationInterceptor() {
//        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
//        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
//        paginationInnerInterceptor.setOverflow(false);
//        paginationInnerInterceptor.setMaxLimit(1000L);
//        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);
//        return mybatisPlusInterceptor;
//    }

}
