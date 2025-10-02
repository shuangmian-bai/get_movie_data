package org.example.get_movie_data.controller;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.MovieServiceManager;
import org.example.get_movie_data.service.DataSourceConfig;
import org.example.get_movie_data.service.ConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 电影数据控制器
 * 
 * 提供RESTful API接口用于获取电影相关信息，包括搜索电影、获取剧集和获取M3U8播放地址。
 * 支持通过URL参数动态选择不同的数据源。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/movie")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class MovieController {
    
    private static final Logger logger = Logger.getLogger(MovieController.class.getName());
    
    // 限制最大并发数，避免网络资源紧张
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    
    // 请求间隔（毫秒），避免过于频繁的请求
    private static final int REQUEST_INTERVAL_MS = 500;

    @Autowired
    private MovieServiceManager movieServiceManager;
    
    @Autowired
    private ConfigManager configManager;

    /**
     * 处理跨域预检请求
     * 
     * @return ResponseEntity
     */
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }
    
    /**
     * 根据搜索关键词获取影视信息（完整信息，包含剧集）
     * 此接口为对外统一接口，会并发向所有配置的数据源发送HTTP请求并整合结果
     * 
     * @param keyword 搜索关键词
     * @return 影视信息列表
     */
    @GetMapping("/search/all")
    public List<Movie> searchMoviesFromAllSources(@RequestParam String keyword) {
        logger.info("MovieController.searchMoviesFromAllSources called with keyword: " + keyword);
        
        // 获取所有URL映射配置
        DataSourceConfig config = configManager.getConfig();
        List<DataSourceConfig.UrlMapping> urlMappings = config.getUrlMappings();
        
        if (urlMappings == null || urlMappings.isEmpty()) {
            logger.warning("No url mappings configured");
            return new ArrayList<>();
        }
        
        // 使用信号量限制最大并发数
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
        
        // 创建线程池，限制并发线程数
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        
        try {
            // 存储所有Future结果
            List<Future<List<Movie>>> futures = new ArrayList<>();
            
            // 向每个URL映射提交任务
            for (int i = 0; i < urlMappings.size(); i++) {
                DataSourceConfig.UrlMapping urlMapping = urlMappings.get(i);
                // 跳过通配符匹配
                if ("*".equals(urlMapping.getBaseUrl())) {
                    continue;
                }
                
                // 在提交任务前增加延迟，避免同时发起大量请求
                final int index = i; // 保存循环索引用于日志
                Future<List<Movie>> future = executor.submit(() -> {
                    try {
                        // 获取信号量许可
                        semaphore.acquire();
                        
                        // 添加请求间隔，避免过于频繁的请求
                        if (index > 0) {
                            Thread.sleep(REQUEST_INTERVAL_MS);
                        }
                        
                        logger.info("Searching movies from URL: " + urlMapping.getBaseUrl());
                        // 直接调用内部方法而不是通过HTTP请求
                        MovieService service = movieServiceManager.getMovieServiceByBaseUrl(urlMapping.getBaseUrl());
                        List<Movie> movies = service.searchMovies(urlMapping.getBaseUrl(), keyword);
                        
                        // 为每个电影对象设置baseUrl字段
                        if (movies != null) {
                            for (Movie movie : movies) {
                                movie.setBaseUrl(urlMapping.getBaseUrl());
                            }
                            return movies;
                        }
                        return new ArrayList<>();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error searching movies from URL " + urlMapping.getBaseUrl(), e);
                        return new ArrayList<>();
                    } finally {
                        // 释放信号量许可
                        semaphore.release();
                    }
                });
                futures.add(future);
            }
            
            // 收集所有结果
            List<Movie> allMovies = new ArrayList<>();
            for (Future<List<Movie>> future : futures) {
                try {
                    List<Movie> movies = future.get(30, TimeUnit.SECONDS); // 30秒超时
                    allMovies.addAll(movies);
                } catch (TimeoutException e) {
                    logger.log(Level.WARNING, "Timeout getting result from future", e);
                    future.cancel(true); // 取消任务
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting result from future", e);
                }
            }
            
            logger.info("Total movies found from all sources: " + allMovies.size());
            return allMovies;
        } finally {
            // 关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    /**
     * 根据播放地址获取影视的全部集数和标题以及播放地址
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param playUrl 播放地址
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视剧集列表
     */
    @GetMapping("/episodes")
    public List<Movie.Episode> getEpisodes(@RequestParam String baseUrl, 
                                          @RequestParam String playUrl,
                                          @RequestParam(required = false) String datasource) {
        logger.info("MovieController.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl + ", datasource: " + datasource);
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl(baseUrl);
        return service.getEpisodes(baseUrl, playUrl);
    }

    /**
     * 获取具体播放地址的m3u8
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param episodeUrl 具体播放地址
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 包含m3u8地址的响应
     */
    @GetMapping("/m3u8")
    public MovieResponse getM3u8Url(@RequestParam String baseUrl, 
                            @RequestParam String episodeUrl,
                            @RequestParam(required = false) String datasource) {
        logger.info("MovieController.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl + ", datasource: " + datasource);
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl(baseUrl);
        String m3u8Url = service.getM3u8Url(baseUrl, episodeUrl);
        
        MovieResponse response = new MovieResponse();
        response.setMovie(m3u8Url);
        return response;
    }
}