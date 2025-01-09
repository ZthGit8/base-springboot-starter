package com.my.base.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.my.base.common.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import org.apache.commons.lang3.StringUtils;
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
        if (StringUtils.isNotBlank(request.getHeader("traceId"))){
            requestInfo.setTraceId(request.getHeader("traceId"));
        } else {
            requestInfo.setTraceId(UUID.randomUUID().toString());
        }
        if (StringUtils.isNotBlank(request.getHeader("timestamp"))) {
            requestInfo.setTimestamp(Long.parseLong(request.getHeader("timestamp")));
        } else {
            requestInfo.setTimestamp(System.currentTimeMillis());
        }
        if (StringUtils.isNotBlank(request.getHeader("nonce"))){
            requestInfo.setNonce(request.getHeader("nonce"));
        }
        if (StringUtils.isNotBlank(request.getHeader("sign"))) {
            requestInfo.setSign(request.getHeader("sign"));
        }
        requestInfo.setMethod(request.getMethod());
        RequestContext.setRequestInfo(requestInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        RequestContext.remove();
    }

}
