package com.my.base;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan("com.my.base.**.mapper")
@SpringBootApplication
public class BaseApplication {
    public static void main(String[] args) {
        SpringApplication.run( BaseApplication.class, args);
    }
}
