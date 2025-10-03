package org.example.get_movie_data.controller;

import org.example.get_movie_data.service.MovieServiceManager;
import org.example.get_movie_data.service.ConfigManager;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.DataSourceConfig;
import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MovieController测试类
 * 
 * 用于测试电影控制器的各项功能，使用Spring Boot测试框架和Mockito进行模拟
 */
@SpringBootTest
@SpringJUnitConfig(TestConfig.class)
@ActiveProfiles("test")
public class MovieControllerTest {

    @InjectMocks
    private MovieController movieController;

    @Mock
    private MovieServiceManager movieServiceManager;

    @Mock
    private ConfigManager configManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // 使用反射设置私有字段
        ReflectionTestUtils.setField(movieController, "movieServiceManager", movieServiceManager);
        ReflectionTestUtils.setField(movieController, "configManager", configManager);
    }

    @Test
    public void testHandleOptions() {
        // 测试处理跨域预检请求
        assertNotNull(movieController.handleOptions());
    }

    @Test
    public void testSearchMoviesFromAllSources() {
        // 准备测试数据
        String keyword = "测试电影";
        
        // 模拟配置管理器
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        
        // 创建URL映射配置
        List<DataSourceConfig.UrlMapping> urlMappings = new ArrayList<>();
        DataSourceConfig.UrlMapping testMapping = new DataSourceConfig.UrlMapping();
        testMapping.setBaseUrl("https://127.0.0.1/test");
        testMapping.setDatasource("test");
        urlMappings.add(testMapping);
        
        // 添加通配符映射
        DataSourceConfig.UrlMapping wildcardMapping = new DataSourceConfig.UrlMapping();
        wildcardMapping.setBaseUrl("*");
        wildcardMapping.setDatasource("default");
        urlMappings.add(wildcardMapping);
        
        dataSourceConfig.setUrlMappings(urlMappings);
        when(configManager.getConfig()).thenReturn(dataSourceConfig);
        
        // 模拟MovieService
        MovieService movieService = mock(MovieService.class);
        when(movieServiceManager.getMovieServiceByBaseUrl(anyString())).thenReturn(movieService);
        
        // 模拟返回结果
        List<Movie> mockMovies = new ArrayList<>();
        Movie movie = new Movie();
        movie.setName("测试电影");
        movie.setDescription("这是一部测试电影");
        movie.setPlayUrl("http://test.com/movie/1");
        movie.setBaseUrl("http://test.com");
        mockMovies.add(movie);
        
        when(movieService.searchMovies(anyString(), eq(keyword))).thenReturn(mockMovies);
        
        // 执行测试
        List<Movie> result = movieController.searchMoviesFromAllSources(keyword);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("测试电影", result.get(0).getName());
    }

    @Test
    public void testGetEpisodes() {
        // 准备测试数据
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/movie/1";
        
        // 模拟MovieService
        MovieService movieService = mock(MovieService.class);
        when(movieServiceManager.getMovieServiceByBaseUrl(baseUrl)).thenReturn(movieService);
        
        // 模拟返回结果
        List<Movie.Episode> mockEpisodes = new ArrayList<>();
        Movie.Episode episode = new Movie.Episode();
        episode.setTitle("测试剧集");
        episode.setEpisodeUrl("http://test.com/episode/1");
        mockEpisodes.add(episode);
        
        when(movieService.getEpisodes(baseUrl, playUrl)).thenReturn(mockEpisodes);
        
        // 执行测试
        List<Movie.Episode> result = movieController.getEpisodes(baseUrl, playUrl, null);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("测试剧集", result.get(0).getTitle());
    }

    @Test
    public void testGetM3u8Url() {
        // 准备测试数据
        String baseUrl = "http://test.com";
        String episodeUrl = "http://test.com/episode/1";
        
        // 模拟MovieService
        MovieService movieService = mock(MovieService.class);
        when(movieServiceManager.getMovieServiceByBaseUrl(baseUrl)).thenReturn(movieService);
        
        // 模拟返回结果
        String mockM3u8Url = "http://test.com/测试字符/1.m3u8";
        when(movieService.getM3u8Url(baseUrl, episodeUrl)).thenReturn(mockM3u8Url);
        
        // 执行测试
        MovieResponse result = movieController.getM3u8Url(baseUrl, episodeUrl, null);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mockM3u8Url, result.getMovie());
    }
}