package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源管理器
 * 
 * 负责管理所有数据源实例，根据URL映射动态加载和创建数据源服务。
 * 支持通过外部JAR包扩展数据源实现。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
public class DataSourceManager {
    
    @Autowired
    private DataSourceConfig dataSourceConfig;
    
    /** 服务缓存，避免重复创建实例 */
    private Map<String, MovieService> serviceCache = new HashMap<>();
    
    /** 类加载器缓存，避免重复创建类加载器 */
    private Map<String, URLClassLoader> classLoaderCache = new HashMap<>();
    
    /**
     * 初始化方法，在Spring容器启动后执行
     * 注册默认的数据源服务
     */
    @PostConstruct
    public void init() {
        System.out.println("Initializing DataSourceManager...");
        serviceCache.put("default", new DefaultMovieService());
        System.out.println("DataSourceManager initialized with default service");
    }
    
    /**
     * 根据基础URL获取对应的电影服务实例
     * 
     * @param baseUrl 基础URL
     * @return 对应的电影服务实例
     */
    public MovieService getMovieServiceByBaseUrl(String baseUrl) {
        System.out.println("Getting movie service for baseUrl: " + baseUrl);
        
        // 查找匹配的数据源ID
        String datasourceId = findDatasourceIdByBaseUrl(baseUrl);
        System.out.println("Found datasourceId: " + datasourceId);
        
        if (datasourceId == null) {
            System.out.println("No matching datasource found, returning default service");
            return serviceCache.get("default");
        }
        
        // 检查是否是默认数据源
        if ("default".equals(datasourceId)) {
            System.out.println("Using default service");
            return serviceCache.get("default");
        }
        
        // 检查缓存
        if (serviceCache.containsKey(datasourceId)) {
            System.out.println("Found service in cache for datasourceId: " + datasourceId);
            return serviceCache.get(datasourceId);
        }
        
        // 创建新的服务实例
        MovieService service = createMovieService(datasourceId);
        if (service != null) {
            serviceCache.put(datasourceId, service);
            System.out.println("Created and cached service for datasourceId: " + datasourceId);
        } else {
            System.out.println("Failed to create service for datasourceId: " + datasourceId + ", using default");
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
        System.out.println("Finding datasource for baseUrl: " + baseUrl);
        
        if (dataSourceConfig.getUrlMappings() != null) {
            // 先查找精确匹配
            for (DataSourceConfig.UrlMapping mapping : dataSourceConfig.getUrlMappings()) {
                System.out.println("Checking mapping: " + mapping.getBaseUrl() + " -> " + mapping.getDatasource());
                if (baseUrl.equals(mapping.getBaseUrl())) {
                    System.out.println("Found exact match: " + mapping.getDatasource());
                    return mapping.getDatasource();
                }
            }
            
            // 再查找通配符匹配
            for (DataSourceConfig.UrlMapping mapping : dataSourceConfig.getUrlMappings()) {
                if ("*".equals(mapping.getBaseUrl())) {
                    System.out.println("Found wildcard match: " + mapping.getDatasource());
                    return mapping.getDatasource();
                }
            }
        }
        
        System.out.println("No matching datasource found");
        return null;
    }
    
    /**
     * 根据数据源ID创建对应的电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @return 对应的电影服务实例
     */
    private MovieService createMovieService(String datasourceId) {
        System.out.println("Creating movie service for datasourceId: " + datasourceId);
        
        // 查找数据源配置
        DataSourceConfig.Datasource targetDatasource = null;
        if (dataSourceConfig.getDatasources() != null) {
            for (DataSourceConfig.Datasource datasource : dataSourceConfig.getDatasources()) {
                System.out.println("Checking datasource: " + datasource.getId() + " -> " + datasource.getClazz());
                if (datasourceId.equals(datasource.getId())) {
                    targetDatasource = datasource;
                    break;
                }
            }
        }
        
        if (targetDatasource == null) {
            System.out.println("Datasource not found in config: " + datasourceId);
            return null;
        }
        
        String className = targetDatasource.getClazz();
        System.out.println("Trying to load class: " + className);
        
        try {
            // 创建URLClassLoader来加载libs目录下的jar文件
            File libDir = new File("libs");
            System.out.println("Lib directory exists: " + libDir.exists());
            
            if (!libDir.exists()) {
                System.out.println("Lib directory does not exist");
                return null;
            }
            
            File[] jarFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            System.out.println("Found jar files: " + (jarFiles != null ? jarFiles.length : 0));
            
            if (jarFiles == null || jarFiles.length == 0) {
                System.out.println("No jar files found in lib directory");
                return null;
            }
            
            // 创建新的类加载器用于加载插件
            URL[] jarUrls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                jarUrls[i] = jarFiles[i].toURI().toURL();
                System.out.println("Adding JAR to classpath: " + jarUrls[i]);
            }
            
            // 检查缓存中是否已有类加载器
            URLClassLoader classLoader = classLoaderCache.get(datasourceId);
            if (classLoader == null) {
                classLoader = new URLClassLoader(jarUrls, Thread.currentThread().getContextClassLoader());
                classLoaderCache.put(datasourceId, classLoader);
                System.out.println("Created new classloader for datasource: " + datasourceId);
            } else {
                System.out.println("Using cached classloader for datasource: " + datasourceId);
            }
            
            // 尝试加载类
            Class<?> clazz = classLoader.loadClass(className);
            System.out.println("Successfully loaded class: " + clazz.getName());
            
            // 检查类是否实现了ExternalMovieService接口
            if (ExternalMovieService.class.isAssignableFrom(clazz)) {
                System.out.println("Class implements ExternalMovieService, creating adapter");
                Object instance = clazz.getDeclaredConstructor().newInstance();
                return new ExternalMovieServiceAdapter((ExternalMovieService) instance);
            }
            
            // 检查类是否实现了MovieService接口
            if (MovieService.class.isAssignableFrom(clazz)) {
                System.out.println("Class implements MovieService, creating direct instance");
                return (MovieService) clazz.getDeclaredConstructor().newInstance();
            }
            
            // 如果类没有实现任何接口，创建一个通用适配器
            System.out.println("Class does not implement required interface, creating generic adapter");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return new GenericExternalServiceAdapter(instance, classLoader);
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: " + className);
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("Error creating movie service:");
            e.printStackTrace();
            return null;
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
            System.out.println("DefaultMovieService.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                System.out.println("Returning test data for https://127.0.0.1/test");
                List<Movie> movies = new ArrayList<>();
                
                Movie movie = new Movie();
                movie.setName("测试电影");
                movie.setDescription("这是一部用于测试的固定电影数据");
                movie.setFinished(true);
                movie.setPlayUrl("https://127.0.0.1/test/play/1");
                movie.setEpisodes(5);
                
                movies.add(movie);
                System.out.println("Test data movies count: " + movies.size());
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
            System.out.println("Default data movies count: " + movies.size());
            return movies;
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            System.out.println("DefaultMovieService.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            
            // 针对特定URL返回固定数据
            if ("https://127.0.0.1/test".equals(baseUrl)) {
                List<Movie.Episode> episodes = new ArrayList<>();
                
                for (int i = 1; i <= 5; i++) {
                    Movie.Episode episode = new Movie.Episode();
                    episode.setTitle("测试第" + i + "集");
                    episode.setEpisodeUrl("https://127.0.0.1/test/episode/" + i);
                    episodes.add(episode);
                }
                
                System.out.println("Test episodes count: " + episodes.size());
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
            
            System.out.println("Default episodes count: " + episodes.size());
            return episodes;
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            System.out.println("DefaultMovieService.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            
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
     * 外部电影服务适配器
     * 
     * 用于适配实现了ExternalMovieService接口的外部数据源
     */
    private static class ExternalMovieServiceAdapter implements MovieService {
        private final ExternalMovieService externalService;

        public ExternalMovieServiceAdapter(ExternalMovieService externalService) {
            this.externalService = externalService;
            System.out.println("Created ExternalMovieServiceAdapter");
        }

        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            System.out.println("ExternalMovieServiceAdapter.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            try {
                List<Movie> result = externalService.searchMovies(baseUrl, keyword);
                System.out.println("External service returned " + (result != null ? result.size() : "null") + " movies");
                return result;
            } catch (Exception e) {
                System.err.println("Error in external service searchMovies:");
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            System.out.println("ExternalMovieServiceAdapter.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            try {
                List<Movie.Episode> result = externalService.getEpisodes(baseUrl, playUrl);
                System.out.println("External service returned " + (result != null ? result.size() : "null") + " episodes");
                return result;
            } catch (Exception e) {
                System.err.println("Error in external service getEpisodes:");
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            System.out.println("ExternalMovieServiceAdapter.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            try {
                String result = externalService.getM3u8Url(baseUrl, episodeUrl);
                System.out.println("External service returned m3u8 URL: " + result);
                return result;
            } catch (Exception e) {
                System.err.println("Error in external service getM3u8Url:");
                e.printStackTrace();
                return "";
            }
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            // 适配器不支持通过数据源ID获取服务
            return this;
        }
    }
    
    /**
     * 通用外部服务适配器，用于处理不实现接口的自定义类
     * 
     * 通过反射调用外部类的方法，并进行数据类型转换
     */
    private static class GenericExternalServiceAdapter implements MovieService {
        private final Object externalService;
        private final ClassLoader classLoader;
        private Method searchMoviesMethod;
        private Method getEpisodesMethod;
        private Method getM3u8UrlMethod;

        public GenericExternalServiceAdapter(Object externalService, ClassLoader classLoader) {
            this.externalService = externalService;
            this.classLoader = classLoader;
            System.out.println("Created GenericExternalServiceAdapter for class: " + externalService.getClass().getName());
            
            // 查找方法
            try {
                Class<?> clazz = externalService.getClass();
                searchMoviesMethod = clazz.getMethod("searchMovies", String.class, String.class);
                getEpisodesMethod = clazz.getMethod("getEpisodes", String.class, String.class);
                getM3u8UrlMethod = clazz.getMethod("getM3u8Url", String.class, String.class);
                System.out.println("Found all required methods in external service");
            } catch (Exception e) {
                System.err.println("Error finding methods in external service:");
                e.printStackTrace();
            }
        }

        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            System.out.println("GenericExternalServiceAdapter.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            try {
                if (searchMoviesMethod != null) {
                    Object result = searchMoviesMethod.invoke(externalService, baseUrl, keyword);
                    System.out.println("External service returned result: " + result);
                    
                    // 转换结果
                    if (result instanceof List) {
                        List<?> externalMovies = (List<?>) result;
                        List<Movie> movies = new ArrayList<>();
                        
                        for (Object externalMovie : externalMovies) {
                            Movie movie = convertExternalMovie(externalMovie);
                            if (movie != null) {
                                movies.add(movie);
                            }
                        }
                        
                        System.out.println("Converted " + movies.size() + " movies");
                        return movies;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in external service searchMovies:");
                e.printStackTrace();
            }
            
            return new ArrayList<>();
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            System.out.println("GenericExternalServiceAdapter.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            try {
                if (getEpisodesMethod != null) {
                    Object result = getEpisodesMethod.invoke(externalService, baseUrl, playUrl);
                    System.out.println("External service returned result: " + result);
                    
                    // 转换结果
                    if (result instanceof List) {
                        List<?> externalEpisodes = (List<?>) result;
                        List<Movie.Episode> episodes = new ArrayList<>();
                        
                        for (Object externalEpisode : externalEpisodes) {
                            Movie.Episode episode = convertExternalEpisode(externalEpisode);
                            if (episode != null) {
                                episodes.add(episode);
                            }
                        }
                        
                        System.out.println("Converted " + episodes.size() + " episodes");
                        return episodes;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in external service getEpisodes:");
                e.printStackTrace();
            }
            
            return new ArrayList<>();
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            System.out.println("GenericExternalServiceAdapter.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            try {
                if (getM3u8UrlMethod != null) {
                    Object result = getM3u8UrlMethod.invoke(externalService, baseUrl, episodeUrl);
                    System.out.println("External service returned result: " + result);
                    
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in external service getM3u8Url:");
                e.printStackTrace();
            }
            
            return "";
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            // 适配器不支持通过数据源ID获取服务
            return this;
        }
        
        /**
         * 转换外部电影对象为内部Movie对象
         * 
         * @param externalMovie 外部电影对象
         * @return 内部Movie对象
         */
        private Movie convertExternalMovie(Object externalMovie) {
            if (externalMovie == null) return null;
            
            try {
                Movie movie = new Movie();
                
                // 使用反射获取外部电影对象的属性
                Class<?> externalMovieClass = externalMovie.getClass();
                
                try {
                    Method getNameMethod = externalMovieClass.getMethod("getName");
                    Object name = getNameMethod.invoke(externalMovie);
                    movie.setName(name != null ? name.toString() : "");
                } catch (Exception e) {
                    System.err.println("Error getting name from external movie: " + e.getMessage());
                }
                
                try {
                    Method getDescriptionMethod = externalMovieClass.getMethod("getDescription");
                    Object description = getDescriptionMethod.invoke(externalMovie);
                    movie.setDescription(description != null ? description.toString() : "");
                } catch (Exception e) {
                    System.err.println("Error getting description from external movie: " + e.getMessage());
                }
                
                try {
                    Method isFinishedMethod = externalMovieClass.getMethod("isFinished");
                    Object finished = isFinishedMethod.invoke(externalMovie);
                    movie.setFinished(finished instanceof Boolean ? (Boolean) finished : false);
                } catch (Exception e) {
                    System.err.println("Error getting finished from external movie: " + e.getMessage());
                }
                
                try {
                    Method getPlayUrlMethod = externalMovieClass.getMethod("getPlayUrl");
                    Object playUrl = getPlayUrlMethod.invoke(externalMovie);
                    movie.setPlayUrl(playUrl != null ? playUrl.toString() : "");
                } catch (Exception e) {
                    System.err.println("Error getting playUrl from external movie: " + e.getMessage());
                }
                
                try {
                    Method getEpisodesMethod = externalMovieClass.getMethod("getEpisodes");
                    Object episodes = getEpisodesMethod.invoke(externalMovie);
                    movie.setEpisodes(episodes instanceof Integer ? (Integer) episodes : 0);
                } catch (Exception e) {
                    System.err.println("Error getting episodes from external movie: " + e.getMessage());
                }
                
                return movie;
            } catch (Exception e) {
                System.err.println("Error converting external movie: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        
        /**
         * 转换外部剧集对象为内部Episode对象
         * 
         * @param externalEpisode 外部剧集对象
         * @return 内部Episode对象
         */
        private Movie.Episode convertExternalEpisode(Object externalEpisode) {
            if (externalEpisode == null) return null;
            
            try {
                Movie.Episode episode = new Movie.Episode();
                
                // 使用反射获取外部剧集对象的属性
                Class<?> externalEpisodeClass = externalEpisode.getClass();
                
                try {
                    Method getTitleMethod = externalEpisodeClass.getMethod("getTitle");
                    Object title = getTitleMethod.invoke(externalEpisode);
                    episode.setTitle(title != null ? title.toString() : "");
                } catch (Exception e) {
                    System.err.println("Error getting title from external episode: " + e.getMessage());
                }
                
                try {
                    Method getEpisodeUrlMethod = externalEpisodeClass.getMethod("getEpisodeUrl");
                    Object episodeUrl = getEpisodeUrlMethod.invoke(externalEpisode);
                    episode.setEpisodeUrl(episodeUrl != null ? episodeUrl.toString() : "");
                } catch (Exception e) {
                    System.err.println("Error getting episodeUrl from external episode: " + e.getMessage());
                }
                
                return episode;
            } catch (Exception e) {
                System.err.println("Error converting external episode: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }
}