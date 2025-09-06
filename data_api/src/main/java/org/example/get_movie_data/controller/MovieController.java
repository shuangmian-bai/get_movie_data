package org.example.get_movie_data.controller;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.model.MovieSimple;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.MovieServiceRouter;
import org.example.get_movie_data.service.DataSourceConfig;
import org.example.get_movie_data.service.ConfigManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Collectors;
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

    @Autowired
    private MovieServiceRouter movieServiceRouter;
    
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
     * 此接口需要指定baseUrl来选择特定数据源
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param keyword 搜索关键词
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视信息列表
     */
    @GetMapping("/search")
    public List<Movie> searchMovies(@RequestParam String baseUrl, 
                                   @RequestParam String keyword,
                                   @RequestParam(required = false) String datasource) {
        logger.info("MovieController.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword + ", datasource: " + datasource);
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        return service.searchMovies(baseUrl, keyword);
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
        
        // 创建RestTemplate用于发送HTTP请求
        RestTemplate restTemplate = new RestTemplate();
        
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(urlMappings.size(), 10));
        
        // 存储所有Future结果
        List<Future<List<Movie>>> futures = new ArrayList<>();
        
        // 向每个URL映射提交任务
        for (DataSourceConfig.UrlMapping urlMapping : urlMappings) {
            // 跳过通配符匹配
            if ("*".equals(urlMapping.getBaseUrl())) {
                continue;
            }
            
            Future<List<Movie>> future = executor.submit(() -> {
                try {
                    logger.info("Searching movies from URL: " + urlMapping.getBaseUrl());
                    String url = "http://localhost:8080/api/movie/search?baseUrl=" + 
                                 urlMapping.getBaseUrl() + "&keyword=" + keyword;
                    ResponseEntity<Movie[]> response = restTemplate.getForEntity(url, Movie[].class);
                    Movie[] movies = response.getBody();
                    
                    // 为每个电影对象设置baseUrl字段
                    if (movies != null) {
                        for (Movie movie : movies) {
                            movie.setBaseUrl(urlMapping.getBaseUrl());
                        }
                        return List.of(movies);
                    }
                    return new ArrayList<>();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error searching movies from URL " + urlMapping.getBaseUrl(), e);
                    return new ArrayList<>();
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
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error getting result from future", e);
            }
        }
        
        // 关闭线程池
        executor.shutdown();
        
        logger.info("Total movies found from all sources: " + allMovies.size());
        return allMovies;
    }

    /**
     * 根据搜索关键词获取影视信息（基础信息，不包含剧集）
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param keyword 搜索关键词
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视信息列表（基础信息）
     */
    @GetMapping("/search/simple")
    public List<MovieSimple> searchMoviesSimple(@RequestParam String baseUrl,
                                               @RequestParam String keyword,
                                               @RequestParam(required = false) String datasource) {
        logger.info("MovieController.searchMoviesSimple called with baseUrl: " + baseUrl + ", keyword: " + keyword + ", datasource: " + datasource);
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        List<Movie> movies = service.searchMovies(baseUrl, keyword);
        
        // 转换为MovieSimple对象，去除剧集信息
        return movies.stream().map(movie -> {
            MovieSimple simple = new MovieSimple();
            // 注意：MovieSimple中没有finished字段，但添加了type和region字段
            simple.setName(movie.getName());
            simple.setDescription(movie.getDescription());
            simple.setPlayUrl(movie.getPlayUrl());
            simple.setPoster(movie.getPoster());
            // 根据需要设置type和region的默认值或从movie中提取
            simple.setType(extractTypeFromMovie(movie));
            simple.setRegion(extractRegionFromMovie(movie));
            return simple;
        }).collect(Collectors.toList());
    }

    /**
     * 从Movie对象中提取类型信息（示例实现）
     * 
     * @param movie 电影对象
     * @return 类型信息
     */
    private String extractTypeFromMovie(Movie movie) {
        // 这里可以根据实际需求从Movie对象中提取类型信息
        // 当前为示例实现，返回默认值
        return "未知类型";
    }

    /**
     * 从Movie对象中提取地区信息（示例实现）
     * 
     * @param movie 电影对象
     * @return 地区信息
     */
    private String extractRegionFromMovie(Movie movie) {
        // 这里可以根据实际需求从Movie对象中提取地区信息
        // 当前为示例实现，返回默认值
        return "未知地区";
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
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        return service.getEpisodes(baseUrl, playUrl);
    }

    /**
     * 获取具体播放地址的m3u8
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param episodeUrl 具体播放地址
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return m3u8地址
     */
    @GetMapping("/m3u8")
    public String getM3u8Url(@RequestParam String baseUrl, 
                            @RequestParam String episodeUrl,
                            @RequestParam(required = false) String datasource) {
        logger.info("MovieController.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl + ", datasource: " + datasource);
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        return service.getM3u8Url(baseUrl, episodeUrl);
    }
}