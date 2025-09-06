package org.example.get_movie_data.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS跨域配置类
 * 
 * 用于解决前端访问后端API时的跨域问题
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class CorsConfig {

    /**
     * 配置跨域过滤器
     * 
     * 允许所有来源、请求头和HTTP方法访问API接口
     * 
     * @return CorsFilter实例
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许任何域名访问
        config.addAllowedOriginPattern("*");
        // 允许任何请求头
        config.addAllowedHeader("*");
        // 允许任何HTTP方法（GET, POST, PUT, DELETE等）
        config.addAllowedMethod("*");
        // 允许发送Cookie和认证信息
        config.setAllowCredentials(true);
        // 设置预检请求的有效期，避免重复发送预检请求
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用CORS配置
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}