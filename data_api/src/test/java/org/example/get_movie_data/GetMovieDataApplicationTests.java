package org.example.get_movie_data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 主应用程序测试类
 * 
 * 用于测试电影数据聚合服务的核心功能，使用Spring Boot测试框架
 */
@SpringBootTest
@SpringJUnitConfig
public class GetMovieDataApplicationTests {

    @Test
    public void contextLoads() {
        // 测试Spring上下文是否能正常加载
    }

    @Test
    public void applicationStarts() {
        // 测试应用程序是否能正常启动
        GetMovieDataApplication.main(new String[] {});
    }
}