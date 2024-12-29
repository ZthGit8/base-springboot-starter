package com.my.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

@SpringBootApplication
public class BaseApplication {
    public static void main(String[] args) {
        SpringApplication.run( BaseApplication.class, args);
    }
}
