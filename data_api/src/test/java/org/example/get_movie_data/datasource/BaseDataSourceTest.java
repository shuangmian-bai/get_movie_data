package org.example.get_movie_data.datasource;

import org.example.get_movie_data.model.Movie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源基础测试类
 * 提供通用的测试方法用于测试各种数据源实现
 */
public class BaseDataSourceTest {

    /**
     * 测试搜索电影功能
     *
     * @param service 电影服务实例
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     */
    public static void testSearchMovies(Object service, String baseUrl, String keyword) {
        try {
            // 使用反射调用searchMovies方法
            List<Movie> movies = (List<Movie>) service.getClass()
                    .getMethod("searchMovies", String.class, String.class)
                    .invoke(service, baseUrl, keyword);

            assertNotNull(movies, "搜索结果不应为null");
            System.out.println(service.getClass().getSimpleName() + " 搜索到的电影数量: " + movies.size());

            // 输出部分电影信息
            movies.stream().limit(3).forEach(movie -> {
                System.out.println("电影名称: " + movie.getName());
                System.out.println("电影描述: " + movie.getDescription());
                System.out.println("播放地址: " + movie.getPlayUrl());
                System.out.println("海报地址: " + movie.getPoster());
                System.out.println("-------------------");
            });
        } catch (Exception e) {
            System.err.println("测试搜索电影功能时出错: " + e.getMessage());
            e.printStackTrace();
            fail("测试搜索电影功能时出错: " + e.getMessage());
        }
    }

    /**
     * 测试获取剧集功能
     *
     * @param service 电影服务实例
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     */
    public static void testGetEpisodes(Object service, String baseUrl, String playUrl) {
        try {
            // 使用反射调用getEpisodes方法
            List<Movie.Episode> episodes = (List<Movie.Episode>) service.getClass()
                    .getMethod("getEpisodes", String.class, String.class)
                    .invoke(service, baseUrl, playUrl);

            assertNotNull(episodes, "剧集列表不应为null");
            System.out.println(service.getClass().getSimpleName() + " 获取到的剧集数量: " + episodes.size());

            // 输出部分剧集信息
            episodes.stream().limit(3).forEach(episode -> {
                System.out.println("剧集标题: " + episode.getTitle());
                System.out.println("剧集地址: " + episode.getEpisodeUrl());
                System.out.println("-------------------");
            });
        } catch (Exception e) {
            System.err.println("测试获取剧集功能时出错: " + e.getMessage());
            e.printStackTrace();
            fail("测试获取剧集功能时出错: " + e.getMessage());
        }
    }

    /**
     * 测试获取M3U8地址功能
     *
     * @param service 电影服务实例
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集地址
     */
    public static void testGetM3u8Url(Object service, String baseUrl, String episodeUrl) {
        try {
            // 使用反射调用getM3u8Url方法
            String m3u8Url = (String) service.getClass()
                    .getMethod("getM3u8Url", String.class, String.class)
                    .invoke(service, baseUrl, episodeUrl);

            assertNotNull(m3u8Url, "M3U8地址不应为null");
            System.out.println(service.getClass().getSimpleName() + " 获取到的M3U8地址: " + m3u8Url);
        } catch (Exception e) {
            System.err.println("测试获取M3U8地址功能时出错: " + e.getMessage());
            e.printStackTrace();
            fail("测试获取M3U8地址功能时出错: " + e.getMessage());
        }
    }
}