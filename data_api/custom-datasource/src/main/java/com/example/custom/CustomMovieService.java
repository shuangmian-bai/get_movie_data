package com.example.custom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author custom-datasource team
 * @version 1.0.0
 */
public class CustomMovieService {
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        List<Movie> movies = new ArrayList<>();
        
        // 根据关键词返回不同类型的测试数据
        if ("空".equals(keyword) || "empty".equalsIgnoreCase(keyword)) {
            // 返回空列表
            return movies;
        } else if ("中文".equals(keyword) || "chinese".equalsIgnoreCase(keyword)) {
            // 返回中文测试数据
            Movie movie = new Movie();
            movie.setName("测试电影名称（中文）");
            movie.setDescription("这是一部用于测试的中文电影描述信息，包含各种中文字符和标点符号。");
            movie.setFinished(true);
            movie.setPlayUrl(baseUrl + "/play/" + URLEncoder.encode("中文测试路径", StandardCharsets.UTF_8));
            movie.setEpisodes(24);
            movies.add(movie);
            
            // 添加第二部电影
            Movie movie2 = new Movie();
            movie2.setName("第二部测试电影");
            movie2.setDescription("这是另一部用于测试的中文电影，用来验证多结果返回功能。");
            movie2.setFinished(false);
            movie2.setPlayUrl(baseUrl + "/play/" + URLEncoder.encode("第二部电影路径", StandardCharsets.UTF_8));
            movie2.setEpisodes(10);
            movies.add(movie2);
        } else if ("英文".equals(keyword) || "english".equalsIgnoreCase(keyword)) {
            // 返回英文测试数据
            Movie movie = new Movie();
            movie.setName("Test Movie Name (English)");
            movie.setDescription("This is an English movie description for testing purposes with various English characters and punctuation.");
            movie.setFinished(true);
            movie.setPlayUrl(baseUrl + "/play/english_test");
            movie.setEpisodes(16);
            movies.add(movie);
        } else {
            // 返回默认测试数据
            Movie movie = new Movie();
            movie.setName("自定义数据源电影: " + keyword);
            movie.setDescription("这是通过自定义数据源获取的电影信息");
            movie.setFinished(true);
            movie.setPlayUrl(baseUrl + "/play/custom_" + keyword);
            movie.setEpisodes(12);
            movies.add(movie);
        }
        
        return movies;
    }

    /**
     * 获取指定电影的所有剧集
     * 
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 剧集列表
     */
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        List<Movie.Episode> episodes = new ArrayList<>();
        
        // 根据播放地址返回不同类型的剧集数据
        if (playUrl.contains("chinese")) {
            // 返回中文剧集数据
            for (int i = 1; i <= 8; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("第" + i + "集：中文测试剧集标题");
                episode.setEpisodeUrl(baseUrl + "/episode/" + URLEncoder.encode("中文剧集_"+i, StandardCharsets.UTF_8));
                episodes.add(episode);
            }
        } else if (playUrl.contains("english")) {
            // 返回英文剧集数据
            for (int i = 1; i <= 6; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("Episode " + i + ": English Test Episode Title");
                episode.setEpisodeUrl(baseUrl + "/episode/english_" + i);
                episodes.add(episode);
            }
        } else if (playUrl.contains("empty")) {
            // 返回空剧集列表
            return episodes;
        } else {
            // 返回默认剧集数据
            for (int i = 1; i <= 5; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("自定义第" + i + "集");
                episode.setEpisodeUrl(baseUrl + "/episode/custom_" + i);
                episodes.add(episode);
            }
        }
        
        return episodes;
    }

    /**
     * 获取指定剧集的M3U8播放地址
     * 
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集播放地址
     * @return M3U8播放地址
     */
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 根据剧集URL返回不同类型的M3U8地址
        if (episodeUrl.contains("chinese")) {
            return baseUrl + "/m3u8/" + URLEncoder.encode("中文视频测试", StandardCharsets.UTF_8) + ".m3u8";
        } else if (episodeUrl.contains("english")) {
            return baseUrl + "/m3u8/english_video_test.m3u8";
        } else if (episodeUrl.contains("error")) {
            // 模拟错误情况，返回无效地址
            return "invalid_url";
        } else {
            return baseUrl + "/m3u8/custom_video.m3u8";
        }
    }
}