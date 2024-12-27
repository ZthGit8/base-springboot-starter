package com.my.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.base.common.interceptor.WebRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.Charset;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

         @Override
     public void addInterceptors(InterceptorRegistry registry) {
         registry.addInterceptor(new WebRequestInterceptor()).addPathPatterns("/**").excludePathPatterns("/swagger-ui/**");
     }
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")  // 明确指定允许的源
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }

        };
    }


    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeAll(converters.stream().filter(converter -> converter instanceof MappingJackson2HttpMessageConverter).toList());
        MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
        mappingJackson2HttpMessageConverter.setObjectMapper(new ObjectMapper());
        mappingJackson2HttpMessageConverter.setDefaultCharset(Charset.defaultCharset());
        converters.addLast(mappingJackson2HttpMessageConverter);
    }

}
