package com.my.base.controller.base;

import com.my.base.config.MybatisConfig;
import com.my.base.shared.util.RedisUtil;
import com.my.base.test.dao.UserDao;
import com.my.base.test.domain.User;
import com.my.base.web.domain.RequestInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author
 * @date 2025/1/9 17:28
 * @description:
 */

@SpringBootTest
@ActiveProfiles("dev")
//@ContextConfiguration(classes = MybatisConfig.class)
class TestControllerTest {
    @Autowired
    private UserDao userDao;
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

    @Test
    void test02() {
        // 查询年龄大于18岁的用户
        List<User> list = userDao.query().gt("age", 18).list();
        for (User user : list) {
            System.out.println(user);
        }

    }
}