package com.my.base.config;

import feign.RequestInterceptor;
import com.my.base.common.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 设置请求头（动态添加）
            RequestInfo requestInfo = RequestContext.getRequestInfo();
            template.header("traceId", requestInfo.getTraceId());
            template.header("requestIp", requestInfo.getRequestIp());
        };
    }
}