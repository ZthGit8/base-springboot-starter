package com.my.base.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.my.base.common.interceptor.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Order(-99)
@Component
public class WebRequestInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setRequestIp(request.getRemoteAddr());
        requestInfo.setTimestamp(System.currentTimeMillis());
        if (request.getHeader("traceId") != null){
            requestInfo.setTraceId(request.getHeader("traceId"));
        } else {
            requestInfo.setTraceId(UUID.randomUUID().toString());
        }
        RequestContext.setRequestInfo(requestInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        RequestContext.remove();
    }

}
