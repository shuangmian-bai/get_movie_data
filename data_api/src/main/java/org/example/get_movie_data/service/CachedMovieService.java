package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import java.util.List;
import java.util.logging.Logger;

/**
 * 缓存装饰器类
 * 
 * 为MovieService提供缓存功能
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
public class CachedMovieService implements MovieService {
    private static final Logger logger = Logger.getLogger(CachedMovieService.class.getName());
    
    private final MovieService movieService;
    private final CacheManager cacheManager;
    
    public CachedMovieService(MovieService movieService, CacheManager cacheManager) {
        this.movieService = movieService;
        this.cacheManager = cacheManager;
    }

    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        // 尝试从缓存获取
        List<Movie> cachedResult = cacheManager.getCachedSearchResults(baseUrl, keyword);
        if (cachedResult != null) {
            logger.info("Cache hit for search: " + keyword + " on " + baseUrl);
            return cachedResult;
        }
        
        // 从实际服务获取
        List<Movie> result = movieService.searchMovies(baseUrl, keyword);
        
        // 缓存结果
        if (result != null) {
            cacheManager.cacheSearchResults(baseUrl, keyword, result);
        }
        
        return result;
    }

    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        // 尝试从缓存获取
        List<Movie.Episode> cachedResult = cacheManager.getCachedEpisodes(baseUrl, playUrl);
        if (cachedResult != null) {
            logger.info("Cache hit for episodes: " + playUrl + " on " + baseUrl);
            return cachedResult;
        }
        
        // 从实际服务获取
        List<Movie.Episode> result = movieService.getEpisodes(baseUrl, playUrl);
        
        // 缓存结果
        if (result != null) {
            cacheManager.cacheEpisodes(baseUrl, playUrl, result);
        }
        
        return result;
    }

    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 尝试从缓存获取
        String cachedResult = cacheManager.getCachedM3u8Url(baseUrl, episodeUrl);
        if (cachedResult != null && !cachedResult.isEmpty()) {
            logger.info("Cache hit for m3u8: " + episodeUrl + " on " + baseUrl);
            return cachedResult;
        }
        
        // 从实际服务获取
        String result = movieService.getM3u8Url(baseUrl, episodeUrl);
        
        // 缓存结果
        if (result != null && !result.isEmpty()) {
            cacheManager.cacheM3u8Url(baseUrl, episodeUrl, result);
        }
        
        return result;
    }

    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        return movieService.getMovieServiceByDatasource(datasourceId);
    }
}