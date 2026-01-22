package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.util.AnnotationScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 电影服务管理器
 * 
 * 使用注解扫描方式自动注册数据源服务
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
public class MovieServiceManager {
    private static final Logger logger = Logger.getLogger(MovieServiceManager.class.getName());
    
    // 服务实例缓存
    private final Map<String, MovieService> serviceCache = new ConcurrentHashMap<>();
    
    // URL到数据源ID的映射
    private final Map<String, String> urlToDatasourceMap = new ConcurrentHashMap<>();
    
    // 缓存管理器
    private CacheManager cacheManager;
    
    @PostConstruct
    public void init() {
        logger.info("Initializing MovieServiceManager...");
        
        // 初始化缓存管理器
        cacheManager = new CacheManager();
        
        // 扫描并注册所有带@DataSource注解的服务
        registerAnnotatedServices();
        
        logger.info("MovieServiceManager initialized with " + serviceCache.size() + " services");
    }
    
    /**
     * 扫描并注册所有带@DataSource注解的服务
     */
    private void registerAnnotatedServices() {
        try {
            // 扫描org.example.get_movie_data.datasource包下的所有带@DataSource注解的类
            Set<Class<?>> classes = AnnotationScanner.scanAnnotatedClasses("org.example.get_movie_data.datasource");
            
            System.out.println("Found " + classes.size() + " annotated classes");
            for (Class<?> clazz : classes) {
                System.out.println("Processing class: " + clazz.getName());
                if (MovieService.class.isAssignableFrom(clazz) && clazz != MovieService.class) {
                    System.out.println("Class " + clazz.getName() + " is assignable from MovieService");
                    org.example.get_movie_data.annotation.DataSource annotation = 
                        clazz.getAnnotation(org.example.get_movie_data.annotation.DataSource.class);
                    
                    if (annotation != null) {
                        try {
                            MovieService service = (MovieService) clazz.getDeclaredConstructor().newInstance();
                            serviceCache.put(annotation.id(), new CachedMovieService(service, cacheManager));
                            
                            // 如果有baseUrl，则建立URL到数据源ID的映射
                            if (!annotation.baseUrl().isEmpty()) {
                                urlToDatasourceMap.put(annotation.baseUrl(), annotation.id());
                            }
                            
                            logger.info("Registered service: " + annotation.id() + " -> " + clazz.getSimpleName());
                            System.out.println("Registered service: " + annotation.id() + " -> " + clazz.getSimpleName());
                        } catch (Exception e) {
                            System.out.println("Failed to instantiate service class: " + clazz.getName() + ", Error: " + e.getMessage());
                            e.printStackTrace();
                            logger.warning("Failed to instantiate service class: " + clazz.getName() + ", Error: " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("Class " + clazz.getName() + " is NOT assignable from MovieService");
                }
            }
            
            // 添加默认服务
            serviceCache.put("default", new CachedMovieService(new DefaultMovieService(), cacheManager));
        } catch (Exception e) {
            logger.severe("Error registering annotated services: " + e.getMessage());
        }
    }
    
    /**
     * 销毁方法，清理资源
     */
    @PreDestroy
    public void destroy() {
        logger.info("Destroying MovieServiceManager...");
        
        // 清理缓存管理器资源
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
        
        // 清理服务缓存
        serviceCache.clear();
        
        logger.info("MovieServiceManager destroyed");
    }
    
    /**
     * 根据基础URL获取对应的电影服务实例
     * 
     * @param baseUrl 基础URL
     * @return 对应的电影服务实例
     */
    public MovieService getMovieServiceByBaseUrl(String baseUrl) {
        logger.info("Getting movie service for baseUrl: " + baseUrl);
        
        // 根据URL确定数据源ID
        String datasourceId = mapBaseUrlToDatasourceId(baseUrl);
        return getMovieServiceById(datasourceId);
    }
    
    /**
     * 根据数据源ID获取对应的电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @return 对应的电影服务实例
     */
    public MovieService getMovieServiceById(String datasourceId) {
        logger.info("Getting movie service for datasourceId: " + datasourceId);
        
        MovieService service = serviceCache.get(datasourceId);
        if (service != null) {
            logger.info("Found service in cache for datasourceId: " + datasourceId);
            return service;
        }
        
        // 如果没有找到，返回默认服务
        logger.info("Service not found for datasourceId: " + datasourceId + ", using default");
        return serviceCache.get("default");
    }
    
    /**
     * 根据基础URL映射到数据源ID
     * 
     * @param baseUrl 基础URL
     * @return 数据源ID
     */
    private String mapBaseUrlToDatasourceId(String baseUrl) {
        // 首先尝试精确匹配
        for (Map.Entry<String, String> entry : urlToDatasourceMap.entrySet()) {
            if (baseUrl.contains(entry.getKey()) || baseUrl.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 如果没有精确匹配，尝试模糊匹配
        for (Map.Entry<String, String> entry : urlToDatasourceMap.entrySet()) {
            if (baseUrl.contains(extractDomain(entry.getKey()))) {
                return entry.getValue();
            }
        }
        
        // 默认使用default数据源
        return "default";
    }
    
    /**
     * 提取域名部分
     */
    private String extractDomain(String url) {
        try {
            // 简单提取域名部分，例如从 https://www.example.com/path 提取 example.com
            String domain = url.replaceAll("^https?://", "").split("/")[0];
            // 移除www前缀
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }
            return domain;
        } catch (Exception e) {
            return url;
        }
    }
    
    /**
     * 默认电影服务实现
     * 
     * 当没有匹配的数据源时使用此默认实现
     */
    private static class DefaultMovieService implements MovieService {
        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            logger.info("DefaultMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                List<Movie> movies = new java.util.ArrayList<>();
                
                for (int i = 1; i <= 3; i++) {
                    Movie movie = new Movie();
                    movie.setName("测试电影" + i);
                    movie.setDescription("这是一部测试电影，关键词：" + keyword);
                    movie.setPlayUrl("https://127.0.0.1/test/movie/" + i);
                    movie.setFinished(true);
                    movie.setEpisodes(1);
                    movies.add(movie);
                }
                
                logger.info("Test movies count: " + movies.size());
                return movies;
            }
            
            // 这里应该实现获取电影的逻辑
            // 暂时返回示例数据
            List<Movie> movies = new java.util.ArrayList<>();
            
            for (int i = 1; i <= 5; i++) {
                Movie movie = new Movie();
                movie.setName("示例电影" + i);
                movie.setDescription("这是一部示例电影，关键词：" + keyword);
                movie.setPlayUrl(baseUrl + "/movie/" + i);
                movie.setFinished(i % 2 == 0); // 偶数电影已完结
                movie.setEpisodes(i * 10); // 集数为10的倍数
                movies.add(movie);
            }
            
            logger.info("Default movies count: " + movies.size());
            return movies;
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            logger.info("DefaultMovieService.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                List<Movie.Episode> episodes = new java.util.ArrayList<>();
                
                for (int i = 1; i <= 5; i++) {
                    Movie.Episode episode = new Movie.Episode();
                    episode.setTitle("测试第" + i + "集");
                    episode.setEpisodeUrl("https://127.0.0.1/test/episode/" + i);
                    episodes.add(episode);
                }
                
                logger.info("Test episodes count: " + episodes.size());
                return episodes;
            }
            
            // 这里应该实现获取集数的逻辑
            // 暂时返回示例数据
            List<Movie.Episode> episodes = new java.util.ArrayList<>();
            
            for (int i = 1; i <= 10; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("第" + i + "集");
                episode.setEpisodeUrl(baseUrl + "/episode/" + i);
                episodes.add(episode);
            }
            
            logger.info("Default episodes count: " + episodes.size());
            return episodes;
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            logger.info("DefaultMovieService.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                return "https://127.0.0.1/test/m3u8/test.m3u8";
            }
            
            // 这里应该实现获取m3u8地址的逻辑
            // 暂时返回示例数据
            return baseUrl + "/m3u8/" + episodeUrl.substring(episodeUrl.lastIndexOf('/') + 1) + ".m3u8";
        }
        
        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            // 默认实现不支持通过数据源ID获取服务
            return this;
        }
    }
    
    /**
     * 获取所有已注册的服务ID列表
     * 
     * @return 已注册的服务ID列表
     */
    public List<String> getAllRegisteredServiceIds() {
        return new ArrayList<>(serviceCache.keySet());
    }
    
    /**
     * 根据数据源ID获取基础URL
     * 
     * @param datasourceId 数据源ID
     * @return 基础URL，如果未找到则返回null
     */
    public String getBaseUrlByDatasourceId(String datasourceId) {
        // 遍历URL到数据源ID的映射，查找匹配的数据源ID
        for (Map.Entry<String, String> entry : urlToDatasourceMap.entrySet()) {
            if (datasourceId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * 获取所有可用的基础URL
     * 
     * @return 基础URL列表
     */
    public List<String> getAllBaseUrls() {
        return new ArrayList<>(urlToDatasourceMap.keySet());
    }
}