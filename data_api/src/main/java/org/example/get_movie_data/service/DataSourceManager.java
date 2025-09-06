package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
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
    
    @Autowired
    private ExternalServiceFactory externalServiceFactory;
    
    /** 数据源服务实例缓存 */
    private Map<String, MovieService> serviceCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化方法，在Spring容器启动后执行
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing DataSourceManager");
        // 初始化时预加载所有配置的数据源
        loadAllServices();
    }
    
    /**
     * 预加载所有配置的数据源服务
     */
    private void loadAllServices() {
        DataSourceConfig config = configManager.getConfig();
        if (config != null && config.getDatasources() != null) {
            for (DataSourceConfig.Datasource datasource : config.getDatasources()) {
                try {
                    MovieService service = createMovieService(datasource.getId(), datasource.getClazz());
                    if (service != null) {
                        serviceCache.put(datasource.getId(), service);
                        logger.info("Loaded service for datasource: " + datasource.getId());
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load service for datasource: " + datasource.getId(), e);
                }
            }
        }
    }
    
    /**
     * 根据数据源ID创建对应的电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @param className 类名
     * @return 对应的电影服务实例
     */
    private MovieService createMovieService(String datasourceId, String className) {
        logger.info("Creating movie service for datasourceId: " + datasourceId + ", className: " + className);
        
        // 首先尝试通过外部服务工厂创建
        MovieService externalService = externalServiceFactory.createMovieService(datasourceId, className);
        if (externalService != null) {
            logger.info("Successfully created external service for datasource: " + datasourceId);
            return externalService;
        }
        
        // 如果外部服务创建失败，使用默认服务
        logger.warning("Failed to create external service for datasource: " + datasourceId + 
                      ", using default service instead");
        return new DefaultMovieService();
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
        
        // 获取配置
        DataSourceConfig config = configManager.getConfig();
        if (config == null || config.getUrlMappings() == null) {
            logger.warning("No configuration or URL mappings found, using default service");
            return new DefaultMovieService();
        }
        
        // 查找匹配的URL映射
        for (DataSourceConfig.UrlMapping mapping : config.getUrlMappings()) {
            if (matchesUrlPattern(baseUrl, mapping.getBaseUrl())) {
                // 根据数据源ID获取服务实例
                MovieService service = serviceCache.get(mapping.getDatasource());
                if (service != null) {
                    logger.info("Found service for datasource: " + mapping.getDatasource());
                    return service;
                } else {
                    logger.warning("Service not found in cache for datasource: " + mapping.getDatasource() + 
                                  ", creating new instance");
                    // 如果缓存中没有，尝试创建新的服务实例
                    DataSourceConfig.Datasource datasource = getDatasourceById(config, mapping.getDatasource());
                    if (datasource != null) {
                        service = createMovieService(datasource.getId(), datasource.getClazz());
                        if (service != null) {
                            serviceCache.put(datasource.getId(), service);
                            return service;
                        }
                    }
                }
            }
        }
        
        // 如果没有找到匹配的映射，使用默认服务
        logger.info("No matching URL mapping found, using default service");
        return new DefaultMovieService();
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
     * 默认电影服务实现类
     * 
     * 当外部数据源不可用时，使用此默认实现。
     */
    public static class DefaultMovieService implements MovieService {
        
        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            logger.info("DefaultMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                logger.info("Returning test data for https://127.0.0.1/test");
                List<Movie> movies = new ArrayList<>();
                
                Movie movie = new Movie();
                movie.setName("测试电影");
                movie.setDescription("这是一部用于测试的固定电影数据");
                movie.setFinished(true);
                movie.setPlayUrl("https://127.0.0.1/test/play/1");
                movie.setEpisodes(5);
                
                movies.add(movie);
                logger.info("Test data movies count: " + movies.size());
                return movies;
            }
            
            // 这里应该实现实际的搜索逻辑，根据baseUrl和keyword获取影视信息
            // 暂时返回示例数据
            List<Movie> movies = new ArrayList<>();
            
            Movie movie = new Movie();
            movie.setName("示例电影");
            movie.setDescription("这是一部示例电影的描述");
            movie.setFinished(true);
            movie.setPlayUrl(baseUrl + "/play/123");
            movie.setEpisodes(10);
            
            movies.add(movie);
            logger.info("Default data movies count: " + movies.size());
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