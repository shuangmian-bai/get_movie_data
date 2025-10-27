package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 内部数据源服务实现
 * 
 * 直接在主项目中定义的数据源，使用@DataSource注解标识
 */
@DataSource(
    id = "internal",
    name = "内部数据源",
    description = "直接在主项目中定义的数据源示例",
    baseUrl = "https://internal.example.com",
    version = "1.0.0"
)
public class InternalMovieService {
    
    private static final Logger logger = Logger.getLogger(InternalMovieService.class.getName());
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        logger.info("InternalMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
        
        // 示例实现，实际项目中应该发送HTTP请求并解析响应
        List<Movie> movies = new ArrayList<>();
        
        // 创建示例电影数据
        for (int i = 1; i <= 3; i++) {
            Movie movie = new Movie();
            movie.setName("内部电影 " + i + " - " + keyword);
            movie.setDescription("这是一部来自内部数据源的电影，关键词：" + keyword);
            movie.setPlayUrl(baseUrl + "/movie/" + i);
            movie.setFinished(i % 2 == 0);
            movie.setEpisodes(i * 10);
            movie.setPoster(baseUrl + "/poster/" + i + ".jpg");
            movies.add(movie);
        }

        logger.info("Returning " + movies.size() + " movies");
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
        logger.info("InternalMovieService.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
        
        // 示例实现，实际项目中应该发送HTTP请求并解析响应
        List<Movie.Episode> episodes = new ArrayList<>();
        
        // 从playUrl中提取电影ID
        String movieId = "1";
        if (playUrl.contains("/")) {
            String[] parts = playUrl.split("/");
            movieId = parts[parts.length - 1];
        }
        
        // 创建示例剧集数据
        for (int i = 1; i <= 5; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/" + movieId + "/" + i);
            episodes.add(episode);
        }
        
        logger.info("Returning " + episodes.size() + " episodes");
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
        logger.info("InternalMovieService.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
        
        // 示例实现，实际项目中应该发送HTTP请求并解析响应
        String m3u8Url = baseUrl + "/m3u8/" + episodeUrl.substring(episodeUrl.lastIndexOf('/') + 1) + ".m3u8";
        
        logger.info("Returning m3u8 URL: " + m3u8Url);
        return m3u8Url;
    }
}