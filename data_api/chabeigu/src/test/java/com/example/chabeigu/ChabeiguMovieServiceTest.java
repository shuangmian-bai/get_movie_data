package com.example.chabeigu;

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
            testGetEpisodes(movieService);

            // 测试getM3u8Url方法
            testGetM3u8Url(movieService);

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

        // 打印搜索结果
        System.out.println("搜索结果:");
        for (Movie movie : movies) {
            System.out.println("名称: " + movie.getName());
            System.out.println("描述: " + movie.getDescription());
            System.out.println("是否完结: " + movie.isFinished());
            System.out.println("播放链接: " + movie.getPlayUrl());
            System.out.println("海报: " + movie.getPoster());
            System.out.println("总集数: " + movie.getEpisodes());
            System.out.println("==========================");
        }
    }

    private static void testGetEpisodes(ChabeiguMovieService movieService) throws Exception {
        String baseUrl = "https://www.chabeigu.com";
        String playUrl = "https://www.chabeigu.com/index.php/vod/detail/id/8250.html";

        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);

        System.out.println("剧集列表:");
        for (Movie.Episode episode : episodes) {
            System.out.println("标题: " + episode.getTitle());
            System.out.println("剧集链接: " + episode.getEpisodeUrl());
            System.out.println("==========================");
        }
    }

    private static void testGetM3u8Url(ChabeiguMovieService movieService) throws Exception {
        String baseUrl = "https://www.chabeigu.com";
        String episodeUrl = "https://www.chabeigu.com/index.php/vod/play/id/8250/sid/1/nid/1.html";

        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);

        System.out.println("m3u8链接: " + m3u8Url);
    }
}