package org.example.get_movie_data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Web配置类
 * 
 * 配置CORS跨域支持和其他Web相关配置
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置CORS跨域支持
     * 
     * @param registry CORS注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
    
    /**
     * 配置全局CORS过滤器
     * 
     * @return FilterRegistrationBean<CorsFilter> 过滤器注册Bean
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        // 创建CorsConfiguration对象并进行配置
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许任何域名访问，包括带端口号的地址
        config.addAllowedOriginPattern("*");
        // 允许任何请求头
        config.addAllowedHeader("*");
        // 允许任何HTTP方法
        config.addAllowedMethod("*");
        // 暴露所有响应头
        config.addExposedHeader("*");
        // 不允许携带认证信息（cookies等），这样才能使用通配符
        config.setAllowCredentials(false);
        // 设置预检请求的有效期（秒）
        config.setMaxAge(3600L);

        // 注册CorsConfiguration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // 创建FilterRegistrationBean并设置优先级
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // 设置最高优先级
        
        return bean;
    }
}