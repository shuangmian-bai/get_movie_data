package com.example.custom.datasource.chabeigu;

import java.util.ArrayList;
import java.util.List;

/**
 * 茶杯狐电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author chabeigu team
 * @version 1.0.0
 */
public class ChabeiguMovieService {
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        List<Movie> movies = new ArrayList<>();
        
        Movie movie = new Movie();
        movie.setName("茶杯狐电影: " + keyword);
        movie.setDescription("这是通过茶杯狐数据源获取的电影信息");
        movie.setFinished(true);
        movie.setPlayUrl(baseUrl + "/play/chabeigu_" + keyword);
        movie.setEpisodes(12);
        
        movies.add(movie);
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
        
        for (int i = 1; i <= 5; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("茶杯狐第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/chabeigu_" + i);
            episodes.add(episode);
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
        return baseUrl + "/m3u8/chabeigu_video.m3u8";
    }
}