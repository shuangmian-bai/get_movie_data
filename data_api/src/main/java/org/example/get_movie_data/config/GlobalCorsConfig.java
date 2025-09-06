package org.example.get_movie_data.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局CORS配置类
 * 
 * 提供最全面的跨域支持配置
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class GlobalCorsConfig {

    /**
     * 配置全局CORS过滤器
     * 
     * @return FilterRegistrationBean<CorsFilter> 过滤器注册Bean
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> globalCorsFilter() {
        // 创建CorsConfiguration对象并进行配置
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许任何域名访问
        config.addAllowedOriginPattern("*");
        // 允许任何请求头
        config.addAllowedHeader("*");
        // 允许任何HTTP方法
        config.addAllowedMethod("*");
        // 暴露所有响应头
        config.addExposedHeader("*");
        // 允许携带认证信息（cookies等）
        config.setAllowCredentials(false);
        // 设置预检请求的有效期（秒）
        config.setMaxAge(3600L);

        // 注册CorsConfiguration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // 创建FilterRegistrationBean并设置优先级
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(-1000); // 设置最高优先级
        
        return bean;
    }
}