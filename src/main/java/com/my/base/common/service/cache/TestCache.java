package com.my.base.common.service.cache;

import com.my.base.common.interceptor.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TestCache extends AbstractLocalCache<String, RequestInfo> {
    @Override
    protected Map<String, RequestInfo> load(List<String> req) {
        HashMap<String, RequestInfo> map = new HashMap<>();
        map.put("test", RequestContext.getRequestInfo());
        return map;
    }
}
