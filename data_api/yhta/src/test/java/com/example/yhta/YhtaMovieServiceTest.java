package com.example.yhta;

import java.util.List;

/**
 * YhtaMovieService测试类
 * 
 * 用于测试YHTA电影服务的各项功能
 */
public class YhtaMovieServiceTest {
    
    public static void main(String[] args) {
        try {
            // 测试实例化
            YhtaMovieService movieService = new YhtaMovieService();
            System.out.println("YhtaMovieService 实例化成功");

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

    private static void testSearchMovies(YhtaMovieService movieService) throws Exception {
        String baseUrl = "https://www.yhta.cc";
        String keyword = "铠甲勇士";

        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);

        if (movies == null) {
            throw new AssertionError("searchMovies 返回了 null");
        }

        // 检查是否返回了结果
        if (movies.isEmpty()) {
            System.out.println("searchMovies 返回了空列表，请检查搜索关键词是否有效");
        } else {
            Movie movie = movies.get(0);
            System.out.println("搜索到的第一部电影: " + movie.getName());
        }

        System.out.println("searchMovies 测试通过");
    }

    private static void testGetEpisodes(YhtaMovieService movieService) throws Exception {
        String baseUrl = "https://www.yhta.cc";
        String playUrl = "https://www.yhta.cc/voddetail/1234.html"; // 示例URL，请根据实际情况修改

        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);

        if (episodes == null) {
            throw new AssertionError("getEpisodes 返回了 null");
        }

        // 只检查是否有剧集返回
        if (episodes.isEmpty()) {
            System.out.println("getEpisodes 返回了空列表，请检查播放URL是否有效");
        } else {
            Movie.Episode episode = episodes.get(0);
            System.out.println("获取到的第一集: " + episode.getTitle());
        }

        System.out.println("getEpisodes 测试通过");
    }

    private static void testGetM3u8Url(YhtaMovieService movieService) throws Exception {
        String baseUrl = "https://www.yhta.cc";
        String episodeUrl = "https://www.yhta.cc/vodplay/1234-1-1.html"; // 示例URL，请根据实际情况修改

        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);

        if (m3u8Url == null || m3u8Url.isEmpty()) {
            System.out.println("getM3u8Url 返回了空值，请检查剧集URL是否有效");
        } else {
            System.out.println("获取到的m3u8地址: " + m3u8Url);
        }

        System.out.println("getM3u8Url 测试通过");
    }
}