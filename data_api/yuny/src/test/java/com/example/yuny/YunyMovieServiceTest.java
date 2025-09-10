package com.example.yuny;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 云云TV电影服务测试类
 */
class YunyMovieServiceTest {

    @Test
    void testYunyMovieServiceCreation() {
        // 测试服务类是否能正常创建
        YunyMovieService service = new YunyMovieService();
        assertNotNull(service, "YunyMovieService实例应该能正常创建");
    }

    @Test
    void testSearchMovies() {
        // 测试搜索功能
        YunyMovieService service = new YunyMovieService();
        List<Movie> movies = service.searchMovies("https://www.yuny.tv", "测试");
        assertNotNull(movies, "搜索结果不应为null");
        // 由于是真实网络请求，我们不能保证返回结果为空或非空
        System.out.println("搜索返回的电影数量: " + movies.size());
    }
    
    @Test
    void testSearchMoviesWithSpecialCharacters() {
        // 测试包含特殊字符的搜索关键词
        YunyMovieService service = new YunyMovieService();
        List<Movie> movies = service.searchMovies("https://www.yuny.tv", "铠甲");
        assertNotNull(movies, "搜索结果不应为null");
        System.out.println("包含特殊字符的搜索返回的电影数量: " + movies.size());
    }

    @Test
    void testGetEpisodes() {
        // 测试获取剧集功能
        YunyMovieService service = new YunyMovieService();
        List<Movie.Episode> episodes = service.getEpisodes("https://www.yuny.tv", "https://www.yuny.tv/play/123");
        assertNotNull(episodes, "剧集列表不应为null");
        // 由于是空实现，这里应该返回空列表
        assertTrue(episodes.isEmpty(), "初始实现应返回空列表");
    }

    @Test
    void testGetM3u8Url() {
        // 测试获取m3u8地址功能
        YunyMovieService service = new YunyMovieService();
        String m3u8Url = service.getM3u8Url("https://www.yuny.tv", "https://www.yuny.tv/play/123/1");
        assertNotNull(m3u8Url, "m3u8地址不应为null");
        // 由于是空实现，这里应该返回空字符串
        assertEquals("", m3u8Url, "初始实现应返回空字符串");
    }
}