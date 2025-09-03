package com.example.custom;

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
            
            // 测试searchMovies方法
            testSearchMovies(movieService);
            
            // 测试getEpisodes方法
            testGetEpisodes(movieService);
            
            // 测试getM3u8Url方法
            testGetM3u8Url(movieService);
            
            System.out.println("所有测试通过!");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testSearchMovies(CustomMovieService movieService) {
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
        
        System.out.println("searchMovies 测试通过");
    }
    
    private static void testGetEpisodes(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String playUrl = "http://test.com/play/test";
        
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);
        
        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }
        
        if (episodes.size() != 5) {
            throw new AssertionError("getEpisodes 返回了错误的剧集数量: " + episodes.size());
        }
        
        System.out.println("getEpisodes 测试通过");
    }
    
    private static void testGetM3u8Url(CustomMovieService movieService) {
        String baseUrl = "http://test.com";
        String episodeUrl = "http://test.com/episode/test";
        
        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);
        
        if (m3u8Url == null) {
            throw new AssertionError("getM3u8Url 返回了 null");
        }
        
        if (!baseUrl.equals(m3u8Url.replace("/m3u8/custom_video.m3u8", ""))) {
            throw new AssertionError("m3u8Url 格式不正确: " + m3u8Url);
        }
        
        System.out.println("getM3u8Url 测试通过");
    }
}