package com.example.yhta;

import java.util.List;

/**
 * 影视信息服务接口
 * 提供搜索影视作品、获取剧集信息和解析m3u8播放地址的功能
 * @author 开发者姓名
 * @version 1.0
 * @since 2025-05-13
 */
public interface MovieService {
    /**
     * 根据搜索关键词获取影视信息
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 影视信息列表
     */
    List<Movie> searchMovies(String baseUrl, String keyword);

    /**
     * 根据播放地址获取影视的全部集数和标题以及播放地址
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 影视剧集列表
     */
    List<Movie.Episode> getEpisodes(String baseUrl, String playUrl);

    /**
     * 获取具体播放地址的m3u8
     * @param baseUrl 基础URL
     * @param episodeUrl 具体播放地址
     * @return m3u8地址
     */
    String getM3u8Url(String baseUrl, String episodeUrl);
}