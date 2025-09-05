package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import java.util.List;

/**
 * 电影服务接口
 * 
 * 定义了获取电影数据的核心方法，所有数据源实现都需要实现此接口。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
public interface MovieService {

    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL，用于确定数据源
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    List<Movie> searchMovies(String baseUrl, String keyword);

    /**
     * 获取指定电影的所有剧集
     * 
     * @param baseUrl 基础URL，用于确定数据源
     * @param playUrl 播放地址
     * @return 剧集列表
     */
    List<Movie.Episode> getEpisodes(String baseUrl, String playUrl);

    /**
     * 获取指定剧集的M3U8播放地址
     * 
     * @param baseUrl 基础URL，用于确定数据源
     * @param episodeUrl 剧集播放地址
     * @return M3U8播放地址
     */
    String getM3u8Url(String baseUrl, String episodeUrl);

    /**
     * 根据数据源ID获取电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @return 电影服务实例
     */
    MovieService getMovieServiceByDatasource(String datasourceId);
}