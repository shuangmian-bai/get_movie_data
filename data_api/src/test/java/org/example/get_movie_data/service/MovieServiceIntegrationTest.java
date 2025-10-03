package org.example.get_movie_data.service;

import org.example.get_movie_data.GetMovieDataApplication;
import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 电影服务集成测试类
 * 
 * 用于测试电影服务在Spring环境中的集成情况
 */
@SpringBootTest(classes = GetMovieDataApplication.class)
@SpringJUnitConfig(TestConfig.class)
@ActiveProfiles("test")
public class MovieServiceIntegrationTest {

    @Autowired
    private MovieServiceManager movieServiceManager;

    @Test
    public void testMovieServiceManagerAutowired() {
        // 测试MovieServiceManager是否能正确注入
        assertNotNull(movieServiceManager);
    }

    @Test
    public void testDefaultMovieService() {
        // 测试默认电影服务是否能正常工作
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl("https://127.0.0.1/test");
        assertNotNull(service);
        
        // 测试搜索电影功能
        List<Movie> movies = service.searchMovies("https://127.0.0.1/test", "测试");
        assertNotNull(movies);
        assertFalse(movies.isEmpty());
        
        // 验证返回的电影信息
        Movie movie = movies.get(0);
        assertNotNull(movie.getName());
        assertNotNull(movie.getDescription());
        assertNotNull(movie.getPlayUrl());
    }

    @Test
    public void testWildcardMapping() {
        // 测试通配符映射是否正常工作
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl("https://any-unknown-url.com");
        assertNotNull(service);
        
        // 应该返回默认服务
        List<Movie> movies = service.searchMovies("https://any-unknown-url.com", "测试");
        assertNotNull(movies);
    }
}