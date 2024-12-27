package com.my.base.config;

import cn.hutool.extra.spring.SpringUtil;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Swagger配置
 */
@Configuration
@Profile({"default", "test", "dev"})
public class SwaggerConfig {

    /**
     * 应用名称
     */
    @Value("${project.name}")
    private String appName;

    /**
     * 应用版本
     */
    @Value("${project.version}")
    private String appVersion;

    @Bean
    public OpenApiCustomizer customOpenAPI() {
        return openApi -> openApi
                .info(new Info().title(appName).version(appVersion)
                        .contact(new Contact().name("zengtianhan").email("2674136003@qq.com")));
    }


    /**
     * 动态生成并注册GroupedOpenApi bean，
     * 以便根据控制器的包名进行分组，为Swagger UI提供按组过滤的功能（注入多个不生效）
     */
    //@Bean
    public List<GroupedOpenApi> onApplicationReadyEvent() {
        // 获取RequestMappingHandlerMapping bean，用于处理请求映射
        RequestMappingHandlerMapping requestMappingHandlerMapping = SpringUtil.getBean(RequestMappingHandlerMapping.class);

        // 提取所有处理器方法对应的包名，并过滤出包含"controller"的包名
        List<String> patternList = requestMappingHandlerMapping.getHandlerMethods().values().stream()
                .map(handlerMethod -> handlerMethod.getBeanType().getPackage().getName())
                .filter(s -> s.contains("controller"))
                .toList();

        // 根据包名中"controller"后的部分进行分组
        Map<String, String> group = patternList.stream()
                .collect(Collectors.toMap(
                        s -> s.substring(s.lastIndexOf("controller")),
                        s -> s,
                        (existing, replacement) -> existing // 处理冲突
                ));

        // 遍历分组，创建并注册GroupedOpenApi bean
        return group.entrySet().stream().map(entry -> {
            String k = entry.getKey();
            String v = entry.getValue();
            String basePackage = v.substring(0, v.lastIndexOf("controller"));
            String fullPackage = basePackage +  k;

            // 构建GroupedOpenApi实例，指定组名和匹配路径
            return GroupedOpenApi.builder()
                    .group(k.split("\\.")[1])
                    .packagesToScan(fullPackage)
                    .addOpenApiCustomizer(customOpenAPI())
                    .build();
        }).toList();
    }
}
