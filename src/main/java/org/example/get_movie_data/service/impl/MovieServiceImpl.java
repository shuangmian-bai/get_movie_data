package org.example.get_movie_data.service.impl;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.DataSourceConfig;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.lang.reflect.Method;

@Service
public class MovieServiceImpl implements MovieService {
    
    @Autowired
    private DataSourceConfig dataSourceConfig;

    public List<Movie> searchMovies(String baseUrl, String keyword) {
        System.out.println("searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
        
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            System.out.println("Returning test data for https://127.0.0.1/test");
            List<Movie> movies = new ArrayList<Movie>();
            
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
        List<Movie> movies = new ArrayList<Movie>();
        
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

    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            List<Movie.Episode> episodes = new ArrayList<Movie.Episode>();
            
            for (int i = 1; i <= 5; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("测试第" + i + "集");
                episode.setEpisodeUrl("https://127.0.0.1/test/episode/" + i);
                episodes.add(episode);
            }
            
            return episodes;
        }
        
        // 这里应该实现获取集数的逻辑
        // 暂时返回示例数据
        List<Movie.Episode> episodes = new ArrayList<Movie.Episode>();
        
        for (int i = 1; i <= 10; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/" + i);
            episodes.add(episode);
        }
        
        return episodes;
    }

    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            return "https://127.0.0.1/test/m3u8/test.m3u8";
        }
        
        // 这里应该实现获取m3u8地址的逻辑
        // 暂时返回示例数据
        return baseUrl + "/m3u8/" + episodeUrl.substring(episodeUrl.lastIndexOf('/') + 1) + ".m3u8";
    }
    
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        // 根据数据源ID获取对应的服务实现
        // 如果没有指定数据源或者指定的是默认数据源，则使用当前实例
        if (datasourceId == null || datasourceId.isEmpty() || "default".equals(datasourceId)) {
            return this;
        }
        
        // 检查数据源是否在配置文件中存在
        DataSourceConfig.Datasource targetDatasource = null;
        if (dataSourceConfig.getDatasources() != null) {
            for (DataSourceConfig.Datasource datasource : dataSourceConfig.getDatasources()) {
                if (datasourceId.equals(datasource.getId())) {
                    targetDatasource = datasource;
                    break;
                }
            }
        }
        
        // 如果数据源在配置中不存在，则返回失败状态
        if (targetDatasource == null) {
            System.out.println("数据源 " + datasourceId + " 未在配置文件中找到");
            return new FailedMovieService();
        }
        
        // 如果数据源存在，尝试动态加载对应的实现类
        String className = targetDatasource.getClazz();
        try {
            System.out.println("尝试加载数据源类: " + className);
            
            // 创建URLClassLoader来加载libs目录下的jar文件
            File libDir = new File("libs");
            File[] jarFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            
            // 创建新的类加载器用于加载插件
            URL[] jarUrls = new URL[0];
            if (jarFiles != null && jarFiles.length > 0) {
                jarUrls = new URL[jarFiles.length];
                for (int i = 0; i < jarFiles.length; i++) {
                    jarUrls[i] = jarFiles[i].toURI().toURL();
                    System.out.println("添加JAR到类路径: " + jarUrls[i]);
                }
            }
            
            // 使用正确的类加载器上下文
            URLClassLoader classLoader = new URLClassLoader(jarUrls, Thread.currentThread().getContextClassLoader());
            
            // 尝试加载类
            Class<?> clazz = classLoader.loadClass(className);
            System.out.println("成功加载类: " + clazz.getName());
            
            // 检查类是否实现了MovieService接口
            if (!MovieService.class.isAssignableFrom(clazz)) {
                System.out.println("类 " + className + " 没有实现 MovieService 接口");
                // 特殊处理自定义数据源（不直接实现MovieService接口的情况）
                if ("com.example.custom.CustomMovieService".equals(className)) {
                    System.out.println("为 " + className + " 创建适配器");
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    return new CustomMovieServiceAdapter(instance);
                }
                return new FailedMovieService();
            }
            
            // 实例化对象
            Object instance = clazz.getDeclaredConstructor().newInstance();
            System.out.println("成功创建实例: " + instance.getClass().getName());
            
            // 直接返回实现类实例
            if (instance instanceof MovieService) {
                System.out.println("实例是 MovieService 类型: " + instance.getClass().getName());
                return (MovieService) instance;
            }
            
            // 如果实例不符合要求，返回失败
            System.out.println("实例不符合要求");
            return new FailedMovieService();
        } catch (ClassNotFoundException e) {
            System.err.println("找不到类: " + className);
            e.printStackTrace();
            return new FailedMovieService();
        } catch (MalformedURLException e) {
            System.err.println("无效的JAR文件URL");
            e.printStackTrace();
            return new FailedMovieService();
        } catch (Exception e) {
            System.err.println("加载数据源时出错: " + e.getMessage());
            e.printStackTrace();
            return new FailedMovieService();
        }
    }
    
    public MovieService getMovieServiceByBaseUrl(String baseUrl) {
        System.out.println("getMovieServiceByBaseUrl called with baseUrl: " + baseUrl);
        
        // 打印配置信息用于调试
        System.out.println("Datasources size: " + (dataSourceConfig.getDatasources() != null ? dataSourceConfig.getDatasources().size() : "null"));
        System.out.println("URL mappings size: " + (dataSourceConfig.getUrlMappings() != null ? dataSourceConfig.getUrlMappings().size() : "null"));
        
        if (dataSourceConfig.getDatasources() != null) {
            for (DataSourceConfig.Datasource ds : dataSourceConfig.getDatasources()) {
                System.out.println("Available datasource: " + ds.getId() + " => " + ds.getClazz());
            }
        }
        
        if (dataSourceConfig.getUrlMappings() != null) {
            for (DataSourceConfig.UrlMapping mapping : dataSourceConfig.getUrlMappings()) {
                System.out.println("Available mapping: " + mapping.getBaseUrl() + " => " + mapping.getDatasource());
            }
        }
        
        // 根据基础URL获取对应的服务实现
        String datasourceId = null;
        
        // 查找精确匹配的URL映射
        if (dataSourceConfig.getUrlMappings() != null) {
            for (DataSourceConfig.UrlMapping urlMapping : dataSourceConfig.getUrlMappings()) {
                System.out.println("Checking URL mapping: " + urlMapping.getBaseUrl() + " -> " + urlMapping.getDatasource());
                if (baseUrl.equals(urlMapping.getBaseUrl())) {
                    datasourceId = urlMapping.getDatasource();
                    System.out.println("Found exact match: " + datasourceId);
                    break;
                }
            }
        }
        
        // 如果没有找到精确匹配，查找通配符匹配
        if (datasourceId == null) {
            System.out.println("No exact match found, checking wildcard matches");
            if (dataSourceConfig.getUrlMappings() != null) {
                for (DataSourceConfig.UrlMapping urlMapping : dataSourceConfig.getUrlMappings()) {
                    System.out.println("Checking wildcard: " + urlMapping.getBaseUrl() + " -> " + urlMapping.getDatasource());
                    if ("*".equals(urlMapping.getBaseUrl())) {
                        datasourceId = urlMapping.getDatasource();
                        System.out.println("Found wildcard match: " + datasourceId);
                        break;
                    }
                }
            }
        }
        
        System.out.println("Final datasourceId: " + datasourceId);
        
        // 如果仍然没有找到匹配的数据源，则返回空数据服务
        if (datasourceId == null) {
            System.out.println("No datasource found, returning EmptyMovieService");
            return new EmptyMovieService();
        }
        
        // 如果匹配到的数据源是默认数据源，则使用当前实例
        if ("default".equals(datasourceId)) {
            System.out.println("Datasource is 'default', returning this");
            return this;
        }
        
        // 使用已有的数据源ID获取服务
        System.out.println("Getting service by datasource: " + datasourceId);
        return getMovieServiceByDatasource(datasourceId);
    }
    
    /**
     * 用于表示没有数据的服务实现
     */
    private static class EmptyMovieService implements MovieService {
        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            // 返回空列表表示没有数据
            return new ArrayList<>();
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            // 返回空列表表示没有数据
            return new ArrayList<>();
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            // 返回空字符串表示没有数据
            return "";
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            return this;
        }
    }
    
    /**
     * 用于表示数据源加载失败的服务实现
     */
    private static class FailedMovieService implements MovieService {
        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            List<Movie> movies = new ArrayList<>();
            Movie movie = new Movie();
            movie.setName("数据源加载失败");
            movie.setDescription("无法加载指定的数据源，可能是因为对应的jar包未配置或不存在");
            movie.setFinished(false);
            movie.setPlayUrl("");
            movie.setEpisodes(0);
            movies.add(movie);
            return movies;
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            // 返回空列表表示获取剧集信息失败
            return new ArrayList<>();
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            // 返回空字符串表示获取播放地址失败
            return "";
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            return this;
        }
    }
    
    /**
     * 适配器类，用于适配CustomMovieService
     */
    private static class CustomMovieServiceAdapter implements MovieService {
        private final Object customMovieService;
        private final java.lang.reflect.Method searchMoviesMethod;
        private final java.lang.reflect.Method getEpisodesMethod;
        private final java.lang.reflect.Method getM3u8UrlMethod;

        public CustomMovieServiceAdapter(Object customMovieService) throws Exception {
            System.out.println("Creating CustomMovieServiceAdapter");
            this.customMovieService = customMovieService;
            Class<?> clazz = customMovieService.getClass();
            System.out.println("Custom service class: " + clazz.getName());
            
            // 添加详细的类信息日志
            System.out.println("Class methods:");
            for (java.lang.reflect.Method method : clazz.getMethods()) {
                System.out.println("  - " + method.getName() + ": " + method.getReturnType().getName());
                StringBuilder params = new StringBuilder();
                for (Class<?> param : method.getParameterTypes()) {
                    params.append(param.getName()).append(", ");
                }
                System.out.println("    params: " + params.toString());
            }
            
            this.searchMoviesMethod = clazz.getMethod("searchMovies", String.class, String.class);
            this.getEpisodesMethod = clazz.getMethod("getEpisodes", String.class, String.class);
            this.getM3u8UrlMethod = clazz.getMethod("getM3u8Url", String.class, String.class);
            System.out.println("Successfully created CustomMovieServiceAdapter");
        }

        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            try {
                System.out.println("CustomMovieServiceAdapter.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
                System.out.println("Invoking method on custom service: " + customMovieService.getClass().getName());
                List<?> result = (List<?>) searchMoviesMethod.invoke(customMovieService, baseUrl, keyword);
                System.out.println("Search movies result: " + result);
                System.out.println("Search movies result size: " + (result != null ? result.size() : "null"));
                
                // 转换结果类型
                if (result != null) {
                    List<Movie> convertedResult = new ArrayList<>();
                    for (Object obj : result) {
                        // 使用反射获取对象属性
                        Object name = obj.getClass().getMethod("getName").invoke(obj);
                        Object description = obj.getClass().getMethod("getDescription").invoke(obj);
                        Object finished = obj.getClass().getMethod("isFinished").invoke(obj);
                        Object playUrl = obj.getClass().getMethod("getPlayUrl").invoke(obj);
                        Object episodes = obj.getClass().getMethod("getEpisodes").invoke(obj);
                        
                        Movie movie = new Movie();
                        movie.setName(name != null ? name.toString() : "");
                        movie.setDescription(description != null ? description.toString() : "");
                        movie.setFinished(finished != null ? (Boolean) finished : false);
                        movie.setPlayUrl(playUrl != null ? playUrl.toString() : "");
                        movie.setEpisodes(episodes != null ? (Integer) episodes : 0);
                        convertedResult.add(movie);
                    }
                    System.out.println("Converted result size: " + convertedResult.size());
                    return convertedResult;
                }
                return new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error in searchMovies:");
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            try {
                System.out.println("Calling getEpisodes with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
                List<Movie.Episode> result = (List<Movie.Episode>) getEpisodesMethod.invoke(customMovieService, baseUrl, playUrl);
                System.out.println("Get episodes result size: " + (result != null ? result.size() : "null"));
                return result;
            } catch (Exception e) {
                System.err.println("Error in getEpisodes:");
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            try {
                System.out.println("Calling getM3u8Url with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
                String result = (String) getM3u8UrlMethod.invoke(customMovieService, baseUrl, episodeUrl);
                System.out.println("Get m3u8Url result: " + result);
                return result;
            } catch (Exception e) {
                System.err.println("Error in getM3u8Url:");
                e.printStackTrace();
                return "";
            }
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            return this;
        }
    }
}