package org.example.get_movie_data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 高优先级CORS过滤器配置类
 * 
 * 用于在过滤器链的早期处理跨域请求，确保所有跨域请求都能被正确处理
 * 兼容Spring Boot 3.x版本
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilterConfig extends OncePerRequestFilter {

    /**
     * 过滤器核心方法，处理跨域请求
     * 
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 允许所有来源
        response.setHeader("Access-Control-Allow-Origin", "*");
        // 允许的请求方法
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH");
        // 允许的请求头
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-Auth-Token, Accept, Origin, Cache-Control");
        // 允许携带认证信息
        response.setHeader("Access-Control-Allow-Credentials", "false");
        // 暴露的响应头
        response.setHeader("Access-Control-Expose-Headers", "*");
        // 预检请求有效期（秒）
        response.setHeader("Access-Control-Max-Age", "3600");
        
        // 如果是预检请求，直接返回200状态
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }
}