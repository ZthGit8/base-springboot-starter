package com.my.base.controller.base;

import com.my.base.common.interceptor.domain.RequestInfo;
import com.my.base.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author
 * @date 2025/1/9 17:28
 * @description:
 */

@SpringBootTest
class TestControllerTest {

    @Test
    void test() {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("test");
        requestInfo.setRequestIp("127.0.0.1");
        requestInfo.setTimestamp(System.currentTimeMillis());
        requestInfo.setTraceId("test");
        RedisUtil.saveBean("test", requestInfo, 10, TimeUnit.MINUTES);
        RequestInfo requestInfo1 = (RequestInfo) RedisUtil.getBean("test", RequestInfo.class);
        System.out.println(requestInfo1);
    }
}