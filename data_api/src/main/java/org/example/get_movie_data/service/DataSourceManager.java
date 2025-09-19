package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 数据源管理器
 * 
 * 负责管理所有数据源服务实例，包括内部默认服务和外部加载的服务。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
public class DataSourceManager implements MovieServiceRouter {
    private static final Logger logger = Logger.getLogger(DataSourceManager.class.getName());
    
    @Autowired
    private ConfigManager configManager;
    
    // 服务实例缓存
    private final Map<String, MovieService> serviceCache = new HashMap<>();
    
    // 缓存管理器
    private CacheManager cacheManager;
    
    // 外部服务工厂
    private ExternalServiceFactory externalServiceFactory;
    
    @PostConstruct
    public void init() {
        logger.info("Initializing DataSourceManager...");
        
        // 初始化缓存管理器
        cacheManager = new CacheManager();
        
        // 初始化外部服务工厂
        externalServiceFactory = new ExternalServiceFactory(cacheManager);
        
        // 添加默认服务
        serviceCache.put("default", new CachedMovieService(new DefaultMovieService(), cacheManager));
        
        logger.info("DataSourceManager initialized with default service");
    }
    
    /**
     * 销毁方法，清理资源
     */
    @PreDestroy
    public void destroy() {
        logger.info("Destroying DataSourceManager...");
        
        // 清理外部服务工厂资源
        if (externalServiceFactory != null) {
            externalServiceFactory.shutdown();
        }
        
        // 清理缓存管理器资源
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
        
        // 清理服务缓存
        serviceCache.clear();
        
        logger.info("DataSourceManager destroyed");
    }
    
    /**
     * 根据基础URL获取对应的电影服务实例
     * 
     * @param baseUrl 基础URL
     * @return 对应的电影服务实例
     */
    @Override
    public MovieService getMovieServiceByBaseUrl(String baseUrl) {
        logger.info("Getting movie service for baseUrl: " + baseUrl);
        
        // 查找匹配的数据源ID
        String datasourceId = findDatasourceIdByBaseUrl(baseUrl);
        logger.info("Found datasourceId: " + datasourceId);
        
        if (datasourceId == null) {
            logger.info("No matching datasource found, returning default service");
            return serviceCache.get("default");
        }
        
        // 检查是否是默认数据源
        if ("default".equals(datasourceId)) {
            logger.info("Using default service");
            return serviceCache.get("default");
        }
        
        // 检查缓存
        if (serviceCache.containsKey(datasourceId)) {
            logger.info("Found service in cache for datasourceId: " + datasourceId);
            return serviceCache.get(datasourceId);
        }
        
        // 创建新的服务实例
        MovieService service = createMovieService(datasourceId);
        if (service != null) {
            serviceCache.put(datasourceId, service);
            logger.info("Created and cached service for datasourceId: " + datasourceId);
        } else {
            logger.info("Failed to create service for datasourceId: " + datasourceId + ", using default");
            service = serviceCache.get("default");
        }
        
        return service;
    }

    /**
     * 根据基础URL查找匹配的数据源ID
     * 
     * @param baseUrl 基础URL
     * @return 匹配的数据源ID，如果没有匹配则返回null
     */
    private String findDatasourceIdByBaseUrl(String baseUrl) {
        logger.info("Finding datasource for baseUrl: " + baseUrl);
        
        DataSourceConfig dataSourceConfig = configManager.getConfig();
        if (dataSourceConfig.getUrlMappings() != null) {
            // 先查找精确匹配
            for (DataSourceConfig.UrlMapping mapping : dataSourceConfig.getUrlMappings()) {
                logger.info("Checking mapping: " + mapping.getBaseUrl() + " -> " + mapping.getDatasource());
                if (baseUrl.equals(mapping.getBaseUrl())) {
                    logger.info("Found exact match: " + mapping.getDatasource());
                    return mapping.getDatasource();
                }
            }
            
            // 再查找通配符匹配
            for (DataSourceConfig.UrlMapping mapping : dataSourceConfig.getUrlMappings()) {
                if ("*".equals(mapping.getBaseUrl())) {
                    logger.info("Found wildcard match: " + mapping.getDatasource());
                    return mapping.getDatasource();
                }
            }
        }
        
        logger.info("No matching datasource found");
        return null;
    }
    
    /**
     * 根据数据源ID创建电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @return 电影服务实例
     */
    private MovieService createMovieService(String datasourceId) {
        logger.info("Creating movie service for datasourceId: " + datasourceId);
        
        DataSourceConfig dataSourceConfig = configManager.getConfig();
        if (dataSourceConfig.getDatasources() == null) {
            logger.warning("No datasources configured");
            return null;
        }
        
        // 查找对应的数据源配置
        DataSourceConfig.Datasource datasourceConfigEntry = null;
        for (DataSourceConfig.Datasource ds : dataSourceConfig.getDatasources()) {
            if (datasourceId.equals(ds.getId())) {
                datasourceConfigEntry = ds;
                break;
            }
        }
        
        if (datasourceConfigEntry == null) {
            logger.warning("Datasource configuration not found for id: " + datasourceId);
            return null;
        }
        
        String className = datasourceConfigEntry.getClazz();
        logger.info("Found class name: " + className);
        
        // 使用外部服务工厂创建服务实例
        return externalServiceFactory.createMovieService(datasourceId, className);
    }
    
    /**
     * 根据ID查找数据源配置
     * 
     * @param config 配置对象
     * @param id 数据源ID
     * @return 数据源配置，如果未找到则返回null
     */
    private DataSourceConfig.Datasource getDatasourceById(DataSourceConfig config, String id) {
        if (config.getDatasources() != null) {
            for (DataSourceConfig.Datasource datasource : config.getDatasources()) {
                if (id.equals(datasource.getId())) {
                    return datasource;
                }
            }
        }
        return null;
    }
    
    /**
     * 检查URL是否匹配指定的模式
     * 
     * @param url 待检查的URL
     * @param pattern URL模式
     * @return 是否匹配
     */
    private boolean matchesUrlPattern(String url, String pattern) {
        if ("*".equals(pattern)) {
            return true; // 通配符匹配所有URL
        }
        
        return url.startsWith(pattern);
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
                List<Movie> movies = new ArrayList<>();
                
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
            List<Movie> movies = new ArrayList<>();
            
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
                List<Movie.Episode> episodes = new ArrayList<>();
                
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
            List<Movie.Episode> episodes = new ArrayList<>();
            
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
}