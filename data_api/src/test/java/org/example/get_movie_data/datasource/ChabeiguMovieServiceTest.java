package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DataSourceTest(value = "chabeigu", name = "茶杯狐数据源测试", description = "测试茶杯狐数据源的各个功能")
public class ChabeiguMovieServiceTest {

    private ChabeiguMovieService service;

    @BeforeEach
    public void setUp() {
        service = new ChabeiguMovieService();
    }

    @Test
    public void testSearchMovies() {
        BaseDataSourceTest.testSearchMovies(service, "https://www.chabeigu.com", "测试");
    }
    
    @Test
    public void testGetEpisodes() {
        BaseDataSourceTest.testGetEpisodes(service, "https://www.chabeigu.com", "https://www.chabeigu.com/test");
    }
    
    @Test
    public void testGetM3u8Url() {
        BaseDataSourceTest.testGetM3u8Url(service, "https://www.chabeigu.com", "https://www.chabeigu.com/test/episode/1");
    }
}