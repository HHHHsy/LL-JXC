package com.jxc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login", "/api/logout");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA 兜底：所有页面路由交给 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/product").setViewName("forward:/index.html");
        registry.addViewController("/customer").setViewName("forward:/index.html");
        registry.addViewController("/supplier").setViewName("forward:/index.html");
        registry.addViewController("/purchase-order").setViewName("forward:/index.html");
        registry.addViewController("/purchase-receive").setViewName("forward:/index.html");
        registry.addViewController("/sale-order").setViewName("forward:/index.html");
        registry.addViewController("/sale-ship").setViewName("forward:/index.html");
        registry.addViewController("/inventory-query").setViewName("forward:/index.html");
        registry.addViewController("/inventory-log").setViewName("forward:/index.html");
        registry.addViewController("/inventory-check").setViewName("forward:/index.html");
        registry.addViewController("/purchase-report").setViewName("forward:/index.html");
        registry.addViewController("/sale-report").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
    }
}
