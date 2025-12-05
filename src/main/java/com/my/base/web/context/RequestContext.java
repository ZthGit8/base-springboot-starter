package com.my.base.web.context;

import com.my.base.web.domain.RequestInfo;

public class RequestContext {

    private static final ThreadLocal<RequestInfo> requestHolder = new ThreadLocal<>();

    public static RequestInfo getRequestInfo() {
        return requestHolder.get();
    }

    public static void setRequestInfo(RequestInfo requestInfo) {
        requestHolder.set(requestInfo);
    }

    public static void remove() {
        requestHolder.remove();
    }
}
