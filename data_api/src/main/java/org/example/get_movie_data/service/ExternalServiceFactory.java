package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 外部服务工厂
 * 
 * 负责加载和创建外部数据源服务实例。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
public class ExternalServiceFactory {
    private static final Logger logger = Logger.getLogger(ExternalServiceFactory.class.getName());

    /** 类加载器缓存，避免重复创建类加载器 */
    private Map<String, URLClassLoader> classLoaderCache = new HashMap<>();

    /**
     * 根据类名创建对应的电影服务实例
     * 
     * @param datasourceId 数据源ID
     * @param className 类名
     * @return 对应的电影服务实例
     */
    public MovieService createMovieService(String datasourceId, String className) {
        logger.info("Creating movie service for datasourceId: " + datasourceId + ", className: " + className);

        try {
            // 创建URLClassLoader来加载libs目录下的jar文件
            File libDir = new File("libs");
            logger.info("Lib directory exists: " + libDir.exists());

            if (!libDir.exists()) {
                logger.warning("Lib directory does not exist");
                return null;
            }

            File[] jarFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            logger.info("Found jar files: " + (jarFiles != null ? jarFiles.length : 0));

            if (jarFiles == null || jarFiles.length == 0) {
                logger.warning("No jar files found in lib directory");
                return null;
            }

            // 创建新的类加载器用于加载插件
            URL[] jarUrls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                jarUrls[i] = jarFiles[i].toURI().toURL();
                logger.info("Adding JAR to classpath: " + jarUrls[i]);
            }

            // 检查缓存中是否已有类加载器
            URLClassLoader classLoader = classLoaderCache.get(datasourceId);
            if (classLoader == null) {
                classLoader = new URLClassLoader(jarUrls, Thread.currentThread().getContextClassLoader());
                classLoaderCache.put(datasourceId, classLoader);
                logger.info("Created new classloader for datasource: " + datasourceId);
            } else {
                logger.info("Using cached classloader for datasource: " + datasourceId);
            }

            // 尝试加载类
            Class<?> clazz = classLoader.loadClass(className);
            logger.info("Successfully loaded class: " + clazz.getName());

            // 检查类是否实现了ExternalMovieService接口
            if (ExternalMovieService.class.isAssignableFrom(clazz)) {
                logger.info("Class implements ExternalMovieService, creating adapter");
                Object instance = clazz.getDeclaredConstructor().newInstance();
                return new ExternalMovieServiceAdapter((ExternalMovieService) instance);
            }

            // 检查类是否实现了MovieService接口
            if (MovieService.class.isAssignableFrom(clazz)) {
                logger.info("Class implements MovieService, creating direct instance");
                return (MovieService) clazz.getDeclaredConstructor().newInstance();
            }

            // 如果类没有实现任何接口，创建一个通用适配器
            logger.info("Class does not implement required interface, creating generic adapter");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return new GenericExternalServiceAdapter(instance, classLoader);
        } catch (ClassNotFoundException e) {
            // 仅记录日志，不打印完整堆栈跟踪，避免误导
            logger.log(Level.INFO, "Class not found: " + className + " for datasource: " + datasourceId + 
                       ". This is normal for optional data sources.", e);
            return null;
        } catch (Exception e) {
            // 仅记录日志，不打印完整堆栈跟踪，避免误导
            logger.log(Level.WARNING, "Error creating movie service for datasource: " + datasourceId + 
                       ", class: " + className + ". Using default service instead.", e);
            return null;
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
            logger.info("Created ExternalMovieServiceAdapter");
        }

        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            logger.info("ExternalMovieServiceAdapter.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            try {
                List<Movie> result = externalService.searchMovies(baseUrl, keyword);
                logger.info("External service returned " + (result != null ? result.size() : "null") + " movies");
                return result;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service searchMovies", e);
                return new ArrayList<>();
            }
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            logger.info("ExternalMovieServiceAdapter.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            try {
                List<Movie.Episode> result = externalService.getEpisodes(baseUrl, playUrl);
                logger.info("External service returned " + (result != null ? result.size() : "null") + " episodes");
                return result;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service getEpisodes", e);
                return new ArrayList<>();
            }
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            logger.info("ExternalMovieServiceAdapter.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            try {
                String result = externalService.getM3u8Url(baseUrl, episodeUrl);
                logger.info("External service returned m3u8 URL: " + result);
                return result;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service getM3u8Url", e);
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
            logger.info("Created GenericExternalServiceAdapter for class: " + externalService.getClass().getName());

            // 查找方法
            try {
                Class<?> clazz = externalService.getClass();
                searchMoviesMethod = clazz.getMethod("searchMovies", String.class, String.class);
                getEpisodesMethod = clazz.getMethod("getEpisodes", String.class, String.class);
                getM3u8UrlMethod = clazz.getMethod("getM3u8Url", String.class, String.class);
                logger.info("Found all required methods in external service");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error finding methods in external service", e);
            }
        }

        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            logger.info("GenericExternalServiceAdapter.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword);
            try {
                if (searchMoviesMethod != null) {
                    Object result = searchMoviesMethod.invoke(externalService, baseUrl, keyword);
                    logger.info("External service returned result: " + result);

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

                        logger.info("Converted " + movies.size() + " movies");
                        return movies;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service searchMovies", e);
            }

            return new ArrayList<>();
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            logger.info("GenericExternalServiceAdapter.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl);
            try {
                if (getEpisodesMethod != null) {
                    Object result = getEpisodesMethod.invoke(externalService, baseUrl, playUrl);
                    logger.info("External service returned result: " + result);

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

                        logger.info("Converted " + episodes.size() + " episodes");
                        return episodes;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service getEpisodes", e);
            }

            return new ArrayList<>();
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            logger.info("GenericExternalServiceAdapter.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl);
            try {
                if (getM3u8UrlMethod != null) {
                    Object result = getM3u8UrlMethod.invoke(externalService, baseUrl, episodeUrl);
                    logger.info("External service returned result: " + result);

                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in external service getM3u8Url", e);
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
                    logger.log(Level.WARNING, "Error getting name from external movie", e);
                }

                try {
                    Method getDescriptionMethod = externalMovieClass.getMethod("getDescription");
                    Object description = getDescriptionMethod.invoke(externalMovie);
                    movie.setDescription(description != null ? description.toString() : "");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting description from external movie", e);
                }

                try {
                    Method isFinishedMethod = externalMovieClass.getMethod("isFinished");
                    Object finished = isFinishedMethod.invoke(externalMovie);
                    movie.setFinished(finished instanceof Boolean ? (Boolean) finished : false);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting finished from external movie", e);
                }

                try {
                    Method getPlayUrlMethod = externalMovieClass.getMethod("getPlayUrl");
                    Object playUrl = getPlayUrlMethod.invoke(externalMovie);
                    movie.setPlayUrl(playUrl != null ? playUrl.toString() : "");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting playUrl from external movie", e);
                }

                try {
                    Method getEpisodesMethod = externalMovieClass.getMethod("getEpisodes");
                    Object episodes = getEpisodesMethod.invoke(externalMovie);
                    movie.setEpisodes(episodes instanceof Integer ? (Integer) episodes : 0);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting episodes from external movie", e);
                }

                try {
                    Method getPosterMethod = externalMovieClass.getMethod("getPoster");
                    Object poster = getPosterMethod.invoke(externalMovie);
                    movie.setPoster(poster != null ? poster.toString() : "");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting poster from external movie", e);
                }

                return movie;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error converting external movie", e);
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
                    logger.log(Level.WARNING, "Error getting title from external episode", e);
                }

                try {
                    Method getEpisodeUrlMethod = externalEpisodeClass.getMethod("getEpisodeUrl");
                    Object episodeUrl = getEpisodeUrlMethod.invoke(externalEpisode);
                    episode.setEpisodeUrl(episodeUrl != null ? episodeUrl.toString() : "");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error getting episodeUrl from external episode", e);
                }

                return episode;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error converting external episode", e);
                return null;
            }
        }
    }
}