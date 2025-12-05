package com.my.base.controller.base;

import com.my.base.shared.annotation.DistributionLock;
import com.my.base.infra.cache.TestCache;
import com.my.base.test.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "test", description = "test")
@RestController
@RequestMapping("/api/test")
public class TestController {
    private final TestCache testCache;

    @Autowired
    public TestController(TestCache testCache) {
        this.testCache = testCache;
    }
    @Operation(summary = "test", description = "test")
    @GetMapping("/hello")
    @DistributionLock(key = "testLock")
    public String sayHello(@Parameter(description = "base") @RequestParam(value = "name05", defaultValue = "World") String name05,
                           @Parameter(description = "base") @RequestParam(value = "name06", defaultValue = "Hello") String name06) {
        testCache.get("testCache");
        return "Hello, World!";
    }

    @Operation(summary = "testDistributionLock", description = "testDistributionLock")
    @PostMapping("/testDistributionLock")
    @DistributionLock(key = "testDistributionLock")
    public String testDistributionLock(@RequestBody User user) {
        return "Hello, World!";
    }
}