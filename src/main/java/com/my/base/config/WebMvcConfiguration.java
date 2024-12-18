package com.my.base.config;

import com.my.base.common.interceptor.WebRequestInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

     @Override
     public void addInterceptors(InterceptorRegistry registry) {
         registry.addInterceptor(new WebRequestInterceptor()).addPathPatterns("/**").excludePathPatterns("/login");
     }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) {
                converters.set(i, converters.getFirst());
                converters.set(0, mappingJackson2HttpMessageConverter);
                break;
            }
        }
    }

}
