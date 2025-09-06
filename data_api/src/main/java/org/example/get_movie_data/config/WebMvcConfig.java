package org.example.get_movie_data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置类
 * 
 * 用于配置Spring MVC相关设置，包括CORS跨域支持
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置跨域请求支持
     * 
     * @param registry CORS注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 对所有路径启用CORS支持
        registry.addMapping("/**")
                // 允许所有来源
                .allowedOriginPatterns("*")
                // 允许的请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                // 允许的请求头
                .allowedHeaders("*")
                // 暴露的响应头
                .exposedHeaders("*")
                // 允许携带认证信息（cookies等）
                .allowCredentials(false)
                // 预检请求有效期（秒）
                .maxAge(3600);
    }
}