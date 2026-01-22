package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 示例数据源实现
 * 
 * 演示如何使用@DataSource注解创建新的数据源
 */
@DataSource(
    id = "example",
    name = "示例数据源",
    description = "这是一个示例数据源，用于演示注解方式的扩展",
    baseUrl = "https://example.com",
    version = "1.0.0"
)
public class ExampleMovieService implements MovieService {
    private static final Logger logger = Logger.getLogger(ExampleMovieService.class.getName());

    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        logger.info("ExampleMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
        
        List<Movie> movies = new ArrayList<>();
        
        // 示例：模拟返回一些电影数据
        for (int i = 1; i <= 3; i++) {
            Movie movie = new Movie();
            movie.setName("示例电影" + i + " - " + keyword);
            movie.setDescription("来自示例数据源的电影描述 - " + keyword);
            movie.setPlayUrl(baseUrl + "/movie/" + i + "?q=" + keyword);
            movie.setFinished(i % 2 == 0); // 偶数电影已完结
            movie.setEpisodes(i * 5); // 集数
            movie.setPoster("https://example.com/poster" + i + ".jpg");
            movies.add(movie);
        }
        
        logger.info("Returning " + movies.size() + " movies for keyword: " + keyword);
        return movies;
    }

    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        logger.info("ExampleMovieService.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
        
        List<Movie.Episode> episodes = new ArrayList<>();
        
        // 示例：从播放URL中提取电影ID，然后生成剧集列表
        String movieId = extractMovieIdFromUrl(playUrl);
        
        for (int i = 1; i <= 10; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/" + movieId + "/" + i);
            episodes.add(episode);
        }
        
        logger.info("Returning " + episodes.size() + " episodes for movie: " + movieId);
        return episodes;
    }

    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        logger.info("ExampleMovieService.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
        
        // 示例：将播放URL转换为M3U8格式
        String m3u8Url = episodeUrl.replace("/episode/", "/stream/") + ".m3u8";
        
        logger.info("Generated M3U8 URL: " + m3u8Url);
        return m3u8Url;
    }

    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        if ("example".equals(datasourceId)) {
            return this;
        }
        return null;
    }
    
    /**
     * 从URL中提取电影ID
     */
    private String extractMovieIdFromUrl(String playUrl) {
        try {
            // 简单地从URL中提取最后一部分作为ID
            String[] parts = playUrl.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            return "unknown";
        }
    }
}