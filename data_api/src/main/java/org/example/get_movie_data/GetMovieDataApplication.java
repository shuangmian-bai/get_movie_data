package org.example.get_movie_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 电影数据聚合服务主应用类
 * 
 * 这是一个基于Spring Boot的Web应用程序，用于聚合多个电影数据源，
 * 提供统一的RESTful API接口用于搜索电影、获取剧集和播放地址。
 * 
 * 应用特性：
 * 1. 支持多个数据源动态加载和切换
 * 2. 提供缓存机制提高性能
 * 3. 支持并发请求处理
 * 4. 具备监控和日志功能
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.example.get_movie_data")
public class GetMovieDataApplication {

    /**
     * 应用程序入口点
     * 
     * 启动Spring Boot应用程序
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GetMovieDataApplication.class, args);
    }
}