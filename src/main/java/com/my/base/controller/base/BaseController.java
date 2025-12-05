package com.my.base.controller.base;

import com.my.base.shared.annotation.DistributionLock;
import com.my.base.infra.cache.TestCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "base", description = "base")
@RestController
@RequestMapping("/api/base")
public class BaseController {
    private final TestCache testCache;

    @Autowired
    public BaseController(TestCache testCache) {
        this.testCache = testCache;
    }
    @Operation(summary = "base", description = "base")
    @GetMapping("/hello")
    @DistributionLock(key = "testLock")
    public String sayHello(@Parameter(description = "base") @RequestParam(value = "name01", defaultValue = "World") String name01,
                           @Parameter(description = "base") @RequestParam(value = "name02", defaultValue = "Hello") String name02) {
        testCache.get("testCache");
        return "Hello, World!";
    }
}