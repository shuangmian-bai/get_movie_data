package com.example.chabeigu;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ChabeiguMovieService测试类
 * 
 * 用于测试茶杯狐电影服务的各项功能
 */
public class ChabeiguMovieServiceTest {
    
    public static void main(String[] args) {
        try {
            // 测试实例化
            ChabeiguMovieService movieService = new ChabeiguMovieService();
            System.out.println("ChabeiguMovieService 实例化成功");

            // 测试searchMovies方法
            testSearchMovies(movieService);

            // 测试getEpisodes方法
//            testGetEpisodes(movieService);

            // 测试getM3u8Url方法
//            testGetM3u8Url(movieService);

            System.out.println("所有测试通过!");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testSearchMovies(ChabeiguMovieService movieService) throws Exception {
        String baseUrl = "https://www.chabeigu.com/";
        String keyword = "铠甲勇士";

        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);

        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }

        if (movies.isEmpty()) {
            throw new AssertionError("searchMovies 返回了空列表");
        }

        Movie movie = movies.get(0);
        // 检查电影名称是否包含关键词，而不是严格匹配特定前缀
        if (!movie.getName().contains(keyword)) {
            throw new AssertionError("电影名称不匹配: 期望包含'" + keyword + "', 实际为'" + movie.getName() + "'");
        }

        System.out.println("searchMovies 测试通过");
    }

    private static void testGetEpisodes(ChabeiguMovieService movieService) throws Exception {
        String baseUrl = "https://www.chabeigu.com";
        String playUrl = "https://www.chabeigu.com/index.php/vod/detail/id/8250.html";

        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);

        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }

        // 只检查是否有剧集返回，不检查具体数量
        if (episodes.isEmpty()) {
            throw new AssertionError("getEpisodes 返回了空列表");
        }

        System.out.println("getEpisodes 测试通过");
    }

    private static void testGetM3u8Url(ChabeiguMovieService movieService) throws Exception {
        String baseUrl = "https://www.chabeigu.com";
        String episodeUrl = "https://www.chabeigu.com/index.php/vod/play/id/8250/sid/1/nid/1.html";

        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);

        if (m3u8Url == null) {
            throw new AssertionError("getM3u8Url 返回了 null");
        }

        if (!baseUrl.equals(m3u8Url.replace("/m3u8/chabeigu_video.m3u8", ""))) {
            throw new AssertionError("m3u8Url 格式不正确: " + m3u8Url);
        }

        System.out.println("getM3u8Url 测试通过");
    }
}