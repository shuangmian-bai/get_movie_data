package com.example.bfzy;

import java.util.List;

/**
 * BfzyMovieService测试类
 * 
 * 用于测试bfzy.tv电影服务的各项功能
 */
public class BfzyMovieServiceTest {
    
    public static void main(String[] args) {
        try {
            // 测试实例化
            BfzyMovieService movieService = new BfzyMovieService();
            System.out.println("BfzyMovieService 实例化成功");

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

    private static void testSearchMovies(BfzyMovieService movieService) throws Exception {
        String baseUrl = "https://bfzy.tv";
        String keyword = "铠甲勇士";

        List<Movie> movies = movieService.searchMovies(baseUrl, keyword);

        //打印搜索结果
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

    private static void testGetEpisodes(BfzyMovieService movieService) throws Exception {
        String baseUrl = "https://bfzy.tv";
        String playUrl = "第01集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第01集/index.m3u8#第02集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第02集/index.m3u8#第03集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第03集/index.m3u8#第04集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第04集/index.m3u8#第05集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第05集/index.m3u8#第06集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第06集/index.m3u8#第07集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第07集/index.m3u8#第08集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第08集/index.m3u8#第09集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第09集/index.m3u8#第10集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第10集/index.m3u8#第11集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第11集/index.m3u8#第12集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第12集/index.m3u8#第13集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第13集/index.m3u8#第14集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第14集/index.m3u8#第15集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第15集/index.m3u8#第16集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第16集/index.m3u8#第17集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第17集/index.m3u8#第18集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第18集/index.m3u8#第19集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第19集/index.m3u8#第20集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第20集/index.m3u8#第21集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第21集/index.m3u8#第22集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第22集/index.m3u8#第23集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第23集/index.m3u8#第24集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第24集/index.m3u8#第25集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第25集/index.m3u8#第26集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第26集/index.m3u8#第27集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第27集/index.m3u8#第28集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第28集/index.m3u8#第29集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第29集/index.m3u8#第30集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第30集/index.m3u8#第31集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第31集/index.m3u8#第32集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第32集/index.m3u8#第33集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第33集/index.m3u8#第34集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第34集/index.m3u8#第35集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第35集/index.m3u8#第36集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第36集/index.m3u8#第37集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第37集/index.m3u8#第38集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第38集/index.m3u8#第39集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第39集/index.m3u8#第40集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第40集/index.m3u8#第41集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第41集/index.m3u8#第42集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第42集/index.m3u8#第43集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第43集/index.m3u8#第44集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第44集/index.m3u8#第45集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第45集/index.m3u8#第46集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第46集/index.m3u8#第47集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第47集/index.m3u8#第48集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第48集/index.m3u8#第49集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第49集/index.m3u8#第50集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第50集/index.m3u8#第51集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第51集/index.m3u8#第52集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第52集/index.m3u8#第53集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第53集/index.m3u8#第54集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第54集/index.m3u8#第55集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第55集/index.m3u8#第56集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第56集/index.m3u8#第57集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第57集/index.m3u8#第58集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第58集/index.m3u8#第59集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第59集/index.m3u8#第60集$https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第60集/index.m3u8";
        List<Movie.Episode> episodes = movieService.getEpisodes(baseUrl, playUrl);

        System.out.println("剧集列表:");
        for (Movie.Episode episode : episodes) {
            System.out.println("标题: " + episode.getTitle());
            System.out.println("剧集链接: " + episode.getEpisodeUrl());
            System.out.println("==========================");
        }
    }

    private static void testGetM3u8Url(BfzyMovieService movieService) throws Exception {
        String baseUrl = "https://bfzy.tv";
        String episodeUrl = "https://c1.rrcdnbf2.com/video/kaijiayongshixingtian/第60集/index.m3u8";

        String m3u8Url = movieService.getM3u8Url(baseUrl, episodeUrl);

        System.out.println("m3u8链接: " + m3u8Url);
    }
}