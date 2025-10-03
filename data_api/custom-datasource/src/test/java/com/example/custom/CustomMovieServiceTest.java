package com.example.custom;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CustomMovieService测试类
 * 
 * 用于测试自定义电影服务的各项功能
 */
public class CustomMovieServiceTest {
    
    public static void main(String[] args) {
        try {
            // 测试实例化
            CustomMovieService movieService = new CustomMovieService();
            System.out.println("CustomMovieService 实例化成功");
            
            // 测试searchMovies方法的各种情况
            testSearchMoviesDefault(movieService);
            testSearchMoviesChinese(movieService);
            testSearchMoviesEnglish(movieService);
            testSearchMoviesEmpty(movieService);
            
            // 测试getEpisodes方法的各种情况
            testGetEpisodesDefault(movieService);
            testGetEpisodesChinese(movieService);
            testGetEpisodesEnglish(movieService);
            testGetEpisodesEmpty(movieService);
            
            // 测试getM3u8Url方法的各种情况
            testGetM3u8UrlDefault(movieService);
            testGetM3u8UrlChinese(movieService);
            testGetM3u8UrlEnglish(movieService);
            
            System.out.println("所有测试通过!");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testSearchMoviesDefault(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String keyword = "test";
        
        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);
        
        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }
        
        if (movies.isEmpty()) {
            throw new AssertionError("searchMovies 返回了空列表");
        }
        
        Movie movie = movies.get(0);
        if (!("自定义数据源电影: " + keyword).equals(movie.getName())) {
            throw new AssertionError("电影名称不匹配");
        }
        
        System.out.println("searchMovies 默认测试通过");
    }
    
    private static void testSearchMoviesChinese(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String keyword = "中文";
        
        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);
        
        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }
        
        if (movies.isEmpty()) {
            throw new AssertionError("searchMovies 返回了空列表");
        }
        
        if (movies.size() != 2) {
            throw new AssertionError("searchMovies 应该返回2个结果，实际返回: " + movies.size());
        }
        
        Movie movie = movies.get(0);
        if (!"测试电影名称（中文）".equals(movie.getName())) {
            throw new AssertionError("中文电影名称不匹配");
        }
        
        // 验证URL中包含中文编码
        String playUrl = movie.getPlayUrl();
        if (!playUrl.contains("%E4%B8%AD%E6%96%87%E6%B5%8B%E8%AF%95%E8%B7%AF%E5%BE%84")) {
            throw new AssertionError("播放URL不包含中文路径编码: " + playUrl);
        }
        
        System.out.println("searchMovies 中文测试通过");
    }
    
    private static void testSearchMoviesEnglish(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String keyword = "english";
        
        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);
        
        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }
        
        if (movies.isEmpty()) {
            throw new AssertionError("searchMovies 返回了空列表");
        }
        
        Movie movie = movies.get(0);
        if (!"Test Movie Name (English)".equals(movie.getName())) {
            throw new AssertionError("英文电影名称不匹配");
        }
        
        System.out.println("searchMovies 英文测试通过");
    }
    
    private static void testSearchMoviesEmpty(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String keyword = "empty";
        
        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);
        
        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }
        
        if (!movies.isEmpty()) {
            throw new AssertionError("searchMovies 应该返回空列表");
        }
        
        System.out.println("searchMovies 空数据测试通过");
    }
    
    private static void testGetEpisodesDefault(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/play/test";
        
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);
        
        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }
        
        if (episodes.size() != 5) {
            throw new AssertionError("getEpisodes 返回了错误的剧集数量: " + episodes.size());
        }
        
        System.out.println("getEpisodes 默认测试通过");
    }
    
    private static void testGetEpisodesChinese(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/play/" + 
            java.net.URLEncoder.encode("中文测试路径", StandardCharsets.UTF_8);
        
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);
        
        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }
        
        if (episodes.size() != 8) {
            throw new AssertionError("getEpisodes 应该返回8个剧集，实际返回: " + episodes.size());
        }
        
        Movie.Episode episode = episodes.get(0);
        if (!episode.getTitle().contains("中文测试剧集标题")) {
            throw new AssertionError("中文剧集标题不匹配");
        }
        
        // 验证剧集URL中包含中文编码
        String episodeUrl = episode.getEpisodeUrl();
        if (!episodeUrl.contains("%E4%B8%AD%E6%96%87%E5%89%A7%E9%9B%86")) {
            throw new AssertionError("剧集URL不包含中文路径编码: " + episodeUrl);
        }
        
        System.out.println("getEpisodes 中文测试通过");
    }
    
    private static void testGetEpisodesEnglish(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/play/english_test";
        
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);
        
        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }
        
        if (episodes.size() != 6) {
            throw new AssertionError("getEpisodes 应该返回6个剧集，实际返回: " + episodes.size());
        }
        
        Movie.Episode episode = episodes.get(0);
        if (!episode.getTitle().contains("English Test Episode Title")) {
            throw new AssertionError("英文剧集标题不匹配");
        }
        
        System.out.println("getEpisodes 英文测试通过");
    }
    
    private static void testGetEpisodesEmpty(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/play/empty";
        
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);
        
        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }
        
        if (!episodes.isEmpty()) {
            throw new AssertionError("getEpisodes 应该返回空列表");
        }
        
        System.out.println("getEpisodes 空数据测试通过");
    }
    
    private static void testGetM3u8UrlDefault(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String episodeUrl = "http://test.com/episode/test";
        
        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);
        
        if (m3u8Url == null) {
            throw new AssertionError("getM3u8Url 返回了 null");
        }
        
        if (!baseUrl.equals(m3u8Url.replace("/m3u8/custom_video.m3u8", ""))) {
            throw new AssertionError("m3u8Url 格式不正确: " + m3u8Url);
        }
        
        System.out.println("getM3u8Url 默认测试通过");
    }
    
    private static void testGetM3u8UrlChinese(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String episodeUrl = "http://test.com/episode/" + 
            java.net.URLEncoder.encode("中文剧集_1", StandardCharsets.UTF_8);
        
        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);
        
        if (m3u8Url == null) {
            throw new AssertionError("getM3u8Url 返回了 null");
        }
        
        // 验证URL中包含中文编码
        if (!m3u8Url.contains("%E4%B8%AD%E6%96%87%E8%A7%86%E9%A2%91%E6%B5%8B%E8%AF%95")) {
            throw new AssertionError("中文m3u8Url 格式不正确: " + m3u8Url);
        }
        
        System.out.println("getM3u8Url 中文测试通过");
    }
    
    private static void testGetM3u8UrlEnglish(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String episodeUrl = "http://test.com/episode/english_1";
        
        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);
        
        if (m3u8Url == null) {
            throw new AssertionError("getM3u8Url 返回了 null");
        }
        
        if (!m3u8Url.endsWith("/m3u8/english_video_test.m3u8")) {
            throw new AssertionError("英文m3u8Url 格式不正确: " + m3u8Url);
        }
        
        System.out.println("getM3u8Url 英文测试通过");
    }
}