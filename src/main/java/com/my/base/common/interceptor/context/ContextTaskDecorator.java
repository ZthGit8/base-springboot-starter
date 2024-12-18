package com.my.base.common.interceptor.context;

import com.my.base.common.interceptor.domain.RequestInfo;
import org.springframework.core.task.TaskDecorator;

public class ContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        RequestInfo requestInfo = RequestContext.getRequestInfo();
        return () -> {
            try {
                RequestContext.setRequestInfo(requestInfo);
                runnable.run();
            } finally {
                RequestContext.remove();
            }
        };
    }
}
