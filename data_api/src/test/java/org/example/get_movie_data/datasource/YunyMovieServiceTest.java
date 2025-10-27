package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DataSourceTest(value = "yuny", name = "云云TV数据源测试", description = "测试云云TV数据源的各个功能")
public class YunyMovieServiceTest {

    private YunyMovieService service;

    @BeforeEach
    public void setUp() {
        service = new YunyMovieService();
    }

    @Test
    public void testSearchMovies() {
        BaseDataSourceTest.testSearchMovies(service, "https://www.yuny.tv", "测试");
    }
    
    @Test
    public void testGetEpisodes() {
        BaseDataSourceTest.testGetEpisodes(service, "https://www.yuny.tv", "https://www.yuny.tv/test");
    }
    
    @Test
    public void testGetM3u8Url() {
        BaseDataSourceTest.testGetM3u8Url(service, "https://www.yuny.tv", "https://www.yuny.tv/test/episode/1");
    }
}