package com.my.base.controller.auth;

import com.my.base.common.annotation.DistributionLock;
import com.my.base.common.service.cache.TestCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "auth", description = "auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final TestCache testCache;

    @Autowired
    public AuthController(TestCache testCache) {
        this.testCache = testCache;
    }
    @Operation(summary = "auth", description = "auth")
    @GetMapping("/hello")
    //@DistributionLock(key = "testLock")
    public String sayHello(@Parameter(description = "base") @RequestParam(value = "name03", defaultValue = "World") String name03,
                           @Parameter(description = "base") @RequestParam(value = "name04", defaultValue = "Hello") String name04) {
        //testCache.get("testCache");
        return "Hello, World!";
    }
}