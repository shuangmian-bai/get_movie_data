package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 带缓存功能的电影服务装饰器
 * 
 * 为MovieService添加缓存功能，避免重复爬取相同数据
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
public class CachedMovieService implements MovieService {
    
    private static final Logger logger = Logger.getLogger(CachedMovieService.class.getName());
    
    private final MovieService delegate;
    private final CacheManager cacheManager;
    
    public CachedMovieService(MovieService delegate, CacheManager cacheManager) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
    }
    
    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        logger.info("CachedMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
        
        // 尝试从缓存获取
        List<Movie> cachedMovies = cacheManager.getCachedSearchResults(baseUrl, keyword);
        if (cachedMovies != null) {
            logger.info("Returning cached search results for baseUrl: " + baseUrl + ", keyword: " + keyword);
            return cachedMovies;
        }
        
        // 缓存未命中，调用实际服务
        logger.info("Cache miss for search results, calling actual service");
        List<Movie> movies = delegate.searchMovies(baseUrl, keyword);
        
        // 缓存结果
        if (movies != null && !movies.isEmpty()) {
            cacheManager.cacheSearchResults(baseUrl, keyword, movies);
        }
        
        return movies;
    }
    
    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        logger.info("CachedMovieService.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
        
        // 尝试从缓存获取
        List<Movie.Episode> cachedEpisodes = cacheManager.getCachedEpisodes(baseUrl, playUrl);
        if (cachedEpisodes != null) {
            logger.info("Returning cached episodes for baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            return cachedEpisodes;
        }
        
        // 缓存未命中，调用实际服务
        logger.info("Cache miss for episodes, calling actual service");
        List<Movie.Episode> episodes = delegate.getEpisodes(baseUrl, playUrl);
        
        // 缓存结果
        if (episodes != null && !episodes.isEmpty()) {
            cacheManager.cacheEpisodes(baseUrl, playUrl, episodes);
        }
        
        return episodes;
    }
    
    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        logger.info("CachedMovieService.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
        
        // 尝试从缓存获取
        String cachedM3u8Url = cacheManager.getCachedM3u8Url(baseUrl, episodeUrl);
        if (cachedM3u8Url != null) {
            logger.info("Returning cached M3U8 URL for baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            return cachedM3u8Url;
        }
        
        // 缓存未命中，调用实际服务
        logger.info("Cache miss for M3U8 URL, calling actual service");
        String m3u8Url = delegate.getM3u8Url(baseUrl, episodeUrl);
        
        // 缓存结果
        if (m3u8Url != null && !m3u8Url.isEmpty()) {
            cacheManager.cacheM3u8Url(baseUrl, episodeUrl, m3u8Url);
        }
        
        return m3u8Url;
    }
    
    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        // 对于获取其他数据源服务的请求，我们不添加缓存层
        return delegate.getMovieServiceByDatasource(datasourceId);
    }
}