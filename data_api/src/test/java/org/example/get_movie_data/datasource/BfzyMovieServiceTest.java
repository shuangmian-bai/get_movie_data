package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DataSourceTest(value = "bfzy", name = "暴风影音数据源测试", description = "测试暴风影音数据源的各个功能")
public class BfzyMovieServiceTest {

    private BfzyMovieService service;

    @BeforeEach
    public void setUp() {
        service = new BfzyMovieService();
    }

    @Test
    public void testSearchMovies() {
        BaseDataSourceTest.testSearchMovies(service, "https://bfzy.tv", "测试");
    }
    
    @Test
    public void testGetEpisodes() {
        // 测试获取剧集功能（使用测试数据）
        String testPlayUrl = "测试第1集$http://example.com/episode1#测试第2集$http://example.com/episode2";
        BaseDataSourceTest.testGetEpisodes(service, "https://bfzy.tv", testPlayUrl);
    }
    
    @Test
    public void testGetM3u8Url() {
        BaseDataSourceTest.testGetM3u8Url(service, "https://bfzy.tv", "http://example.com/test.m3u8");
    }
}