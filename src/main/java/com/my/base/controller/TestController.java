package com.my.base.controller;

import com.my.base.common.annotation.DistributionLock;
import com.my.base.common.service.cache.TestCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    private final TestCache testCache;

    @Autowired
    public TestController(TestCache testCache) {
        this.testCache = testCache;
    }

    @GetMapping("/hello")
    @DistributionLock(key = "testLock")
    public String sayHello(@RequestParam(value = "name01", defaultValue = "World") String name01,
                           @RequestParam(value = "name02", defaultValue = "Hello") String name02) {
        testCache.get("testCache");
        return "Hello, World!";
    }
}