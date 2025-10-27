package org.example.get_movie_data.controller;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.MovieServiceManager;
import org.example.get_movie_data.service.DataSourceConfig;
import org.example.get_movie_data.service.ConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "电影数据接口", description = "提供电影搜索、剧集信息和播放地址获取等功能")
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
     * @param request 搜索请求参数
     * @return 影视信息列表
     */
    @PostMapping("/search/all")
    @Operation(summary = "搜索所有数据源的电影", description = "根据关键词搜索所有数据源的电影信息")
    @ApiResponse(responseCode = "200", description = "成功返回电影列表", 
                 content = @Content(mediaType = "application/json", 
                          schema = @Schema(implementation = Movie.class)))
    public List<Movie> searchMoviesFromAllSources(
            @Parameter(description = "搜索请求参数", required = true) 
            @RequestBody MovieRequest request) {
        String keyword = request.getKeyword();
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
            
            // 用于记录已完成的任务数
            AtomicInteger completedTasks = new AtomicInteger(0);
            final int totalTasks = (int) urlMappings.stream()
                    .filter(mapping -> !"*".equals(mapping.getBaseUrl()))
                    .count();
            
            // 向每个URL映射提交任务
            for (int i = 0; i < urlMappings.size(); i++) {
                DataSourceConfig.UrlMapping urlMapping = urlMappings.get(i);
                // 跳过通配符匹配
                if ("*".equals(urlMapping.getBaseUrl())) {
                    //totalTasks--; // 减少总任务数
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
            
            // 集中输出从各数据源获取到的数据信息
            logger.info("Total movies found from all sources: " + allMovies.size());
            if (logger.isLoggable(Level.INFO)) {
                StringBuilder logBuilder = new StringBuilder();
                logBuilder.append("Detailed data source information:\n");
                
                // 按数据源分组统计
                Map<String, Long> sourceCountMap = allMovies.stream()
                    .collect(Collectors.groupingBy(Movie::getBaseUrl, Collectors.counting()));
                
                for (Map.Entry<String, Long> entry : sourceCountMap.entrySet()) {
                    logBuilder.append("  DataSource: ").append(entry.getKey())
                        .append(", Movie Count: ").append(entry.getValue()).append("\n");
                }
                
                // 输出部分电影信息
                logBuilder.append("Sample movies:\n");
                int count = 0;
                for (Movie movie : allMovies) {
                    if (count++ >= 5) break; // 只显示前5个
                    logBuilder.append("  Name: ").append(movie.getName())
                        .append(", BaseUrl: ").append(movie.getBaseUrl()).append("\n");
                }
                
                if (allMovies.size() > 5) {
                    logBuilder.append("  ... and ").append(allMovies.size() - 5).append(" more movies\n");
                }
                
                logger.info(logBuilder.toString());
            }
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
     * @param request 播放请求参数
     * @return 影视剧集列表
     */
    @PostMapping("/episodes")
    @Operation(summary = "获取影视剧集列表", description = "根据播放地址获取影视的全部集数和标题以及播放地址")
    @ApiResponse(responseCode = "200", description = "成功返回剧集列表", 
                 content = @Content(mediaType = "application/json", 
                          schema = @Schema(implementation = Movie.Episode.class)))
    public List<Movie.Episode> getEpisodes(
            @Parameter(description = "播放请求参数", required = true) 
            @RequestBody MovieRequest request) {
        String baseUrl = request.getBaseUrl();
        String playUrl = request.getPlayUrl();
        String datasource = request.getDatasource();
        logger.info("MovieController.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl + ", datasource: " + datasource);
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl(baseUrl);
        return service.getEpisodes(baseUrl, playUrl);
    }

    /**
     * 获取具体播放地址的m3u8
     * 
     * @param request M3U8请求参数
     * @return 包含m3u8地址的响应
     */
    @PostMapping("/m3u8")
    @Operation(summary = "获取M3U8播放地址", description = "获取具体播放地址的m3u8")
    @ApiResponse(responseCode = "200", description = "成功返回M3U8地址", 
                 content = @Content(mediaType = "application/json", 
                          schema = @Schema(implementation = MovieResponse.class)))
    public MovieResponse getM3u8Url(
            @Parameter(description = "M3U8请求参数", required = true) 
            @RequestBody MovieRequest request) {
        String baseUrl = request.getBaseUrl();
        String episodeUrl = request.getEpisodeUrl();
        String datasource = request.getDatasource();
        logger.info("MovieController.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl + ", datasource: " + datasource);
        MovieService service = movieServiceManager.getMovieServiceByBaseUrl(baseUrl);
        // 直接返回子项目处理后的结果，不做任何额外处理
        String m3u8Url = service.getM3u8Url(baseUrl, episodeUrl);
        
        MovieResponse response = new MovieResponse();
        response.setMovie(m3u8Url);
        return response;
    }
}