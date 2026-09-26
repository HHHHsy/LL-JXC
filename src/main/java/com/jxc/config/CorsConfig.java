package com.jxc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 *
 * <p>生产环境前端由本服务直接托管（同源），无需跨域；开发环境只放行本机 Vite 端口，
 * 不再使用 {@code allowedOriginPatterns("*")} + {@code allowCredentials(true)} 的全开放组合。
 * 鉴权使用 Authorization 请求头而非 Cookie，因此关闭 allowCredentials。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${jxc.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
