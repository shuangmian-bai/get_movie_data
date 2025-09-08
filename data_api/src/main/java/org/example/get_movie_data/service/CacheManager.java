package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 缓存管理器
 * 
 * 用于管理电影数据的文件缓存，避免重复爬取相同数据
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
public class CacheManager {
    
    private static final Logger logger = Logger.getLogger(CacheManager.class.getName());
    
    // 缓存目录
    private static final String SEARCH_CACHE_DIR = "cache/search";
    private static final String EPISODES_CACHE_DIR = "cache/episodes";
    private static final String M3U8_CACHE_DIR = "cache/m3u8";
    
    // 缓存过期时间（毫秒）- 默认2小时
    private static final long CACHE_EXPIRE_TIME = 2 * 60 * 60 * 1000;
    
    // ObjectMapper用于序列化和反序列化
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 内存缓存，避免频繁读取文件
    private final ConcurrentHashMap<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    
    /**
     * 缓存条目内部类
     */
    private static class CacheEntry {
        private final Object data;
        private final long createTime;
        private final long expireTime;
        
        public CacheEntry(Object data) {
            this.data = data;
            this.createTime = System.currentTimeMillis();
            this.expireTime = this.createTime + CACHE_EXPIRE_TIME;
        }
        
        public Object getData() {
            return data;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        public String getExpireTimeString() {
            return LocalDateTime.ofEpochSecond(expireTime/1000, 0, java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分ss秒"));
        }
    }
    
    /**
     * 获取搜索结果缓存
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 缓存的电影列表，如果没有缓存或缓存过期则返回null
     */
    public List<Movie> getCachedSearchResults(String baseUrl, String keyword) {
        String cacheKey = "search_" + baseUrl + "_" + keyword;
        logger.info("Checking cache for key: " + cacheKey);
        
        // 先检查内存缓存
        CacheEntry memoryEntry = memoryCache.get(cacheKey);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            logger.info("Found search results in memory cache for key: " + cacheKey);
            logger.info("Cache will expire at: " + memoryEntry.getExpireTimeString());
            return (List<Movie>) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getSearchCacheFilePath(cacheKey);
            logger.info("Checking file cache at path: " + cacheFile.toString());
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile);
                CacheFileContent<List<Movie>> cacheFileContent = objectMapper.readValue(json, 
                    new TypeReference<CacheFileContent<List<Movie>>>() {});
                
                // 检查是否过期
                if (!cacheFileContent.isExpired()) {
                    List<Movie> movies = cacheFileContent.getData();
                    
                    // 更新内存缓存
                    memoryCache.put(cacheKey, new CacheEntry(movies));
                    
                    logger.info("Loaded search results from file cache for key: " + cacheKey);
                    logger.info("Cache will expire at: " + cacheFileContent.getExpireTimeString());
                    return movies;
                } else {
                    logger.info("File cache expired at: " + cacheFileContent.getExpireTimeString());
                }
            } else {
                logger.info("File cache does not exist for key: " + cacheKey);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading search results from cache", e);
        }
        
        // 检查是否存在更广泛的搜索结果缓存，可以从中提取子集
        logger.info("Checking for subset cache for baseUrl: " + baseUrl + ", keyword: " + keyword);
        List<Movie> subsetMovies = getSubsetFromExistingCache(baseUrl, keyword);
        if (subsetMovies != null) {
            logger.info("Found subset cache with " + subsetMovies.size() + " movies");
            // 将提取的子集缓存起来
            cacheSearchResults(baseUrl, keyword, subsetMovies);
            return subsetMovies;
        } else {
            logger.info("No subset cache found");
        }
        
        return null;
    }
    
    /**
     * 从现有缓存中提取匹配关键词的子集
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 匹配的电影列表，如果没有找到合适的缓存则返回null
     */
    private List<Movie> getSubsetFromExistingCache(String baseUrl, String keyword) {
        logger.info("getSubsetFromExistingCache called with baseUrl: " + baseUrl + ", keyword: " + keyword);
        
        // 遍历文件缓存查找可能的父级缓存
        try {
            Path cacheDir = Paths.get(SEARCH_CACHE_DIR);
            logger.info("Checking file cache directory: " + cacheDir.toString());
            if (Files.exists(cacheDir)) {
                // 遍历所有缓存文件
                try (var files = Files.list(cacheDir)) {
                    for (Path path : (Iterable<Path>) files::iterator) {
                        if (Files.isRegularFile(path) && path.toString().endsWith(".cache")) {
                            try {
                                String fileName = path.getFileName().toString();
                                String key = fileName.substring(0, fileName.length() - 6); // 移除 ".cache" 后缀
                                logger.info("Checking file cache: " + key);
                                
                                // 检查是否为当前baseUrl的搜索缓存
                                if (key.startsWith("search_" + baseUrl + "_")) {
                                    // 检查缓存关键词是否包含搜索关键词
                                    String cachedKeyword = key.substring(("search_" + baseUrl + "_").length());
                                    logger.info("Comparing file cached keyword '" + cachedKeyword + "' with search keyword '" + keyword + "'");
                                    
                                    // 如果缓存关键词包含搜索关键词，则可以从缓存中提取数据
                                    if (cachedKeyword.contains(keyword)) {
                                        logger.info("Found broader search file cache for key: " + key + ", extracting subset for keyword: " + keyword);
                                        String json = Files.readString(path);
                                        CacheFileContent<List<Movie>> cacheFileContent = objectMapper.readValue(json, 
                                            new TypeReference<CacheFileContent<List<Movie>>>() {});
                                        
                                        // 检查是否过期
                                        if (!cacheFileContent.isExpired()) {
                                            List<Movie> cachedMovies = cacheFileContent.getData();
                                            
                                            // 过滤出包含关键词的电影
                                            List<Movie> movies = cachedMovies.stream()
                                                    .filter(movie -> movie.getName().contains(keyword) || 
                                                            (movie.getDescription() != null && movie.getDescription().contains(keyword)))
                                                    .collect(Collectors.toList());
                                            
                                            // 返回过滤后的结果
                                            if (!movies.isEmpty()) {
                                                logger.info("Filtered " + movies.size() + " movies from file cache");
                                                return movies;
                                            }
                                        } else {
                                            logger.info("File cache is expired: " + path.toString() + ", expired at: " + cacheFileContent.getExpireTimeString());
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Error reading cache file: " + path, e);
                            }
                        }
                    }
                }
            } else {
                logger.info("Cache directory does not exist: " + cacheDir.toString());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error listing cache directory", e);
        }
        
        logger.info("No subset cache found for baseUrl: " + baseUrl + ", keyword: " + keyword);
        return null;
    }
    
    /**
     * 根据关键词过滤电影列表
     * 
     * @param movies 电影列表
     * @param keyword 关键词
     * @return 过滤后的电影列表
     */
    private List<Movie> filterMoviesByKeyword(List<Movie> movies, String keyword) {
        return movies.stream()
                .filter(movie -> movie.getName().contains(keyword) || 
                        (movie.getDescription() != null && movie.getDescription().contains(keyword)))
                .collect(Collectors.toList());
    }
    
    /**
     * 缓存搜索结果
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @param movies 电影列表
     */
    public void cacheSearchResults(String baseUrl, String keyword, List<Movie> movies) {
        String cacheKey = "search_" + baseUrl + "_" + keyword;
        
        try {
            // 更新内存缓存
            CacheEntry cacheEntry = new CacheEntry(movies);
            memoryCache.put(cacheKey, cacheEntry);
            
            // 更新文件缓存
            Path cacheFile = getSearchCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            // 创建带过期时间的缓存内容
            CacheFileContent<List<Movie>> cacheFileContent = new CacheFileContent<>(movies, cacheEntry.getExpireTime());
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
            
            logger.info("Cached search results for key: " + cacheKey);
            logger.info("Cache will expire at: " + cacheEntry.getExpireTimeString());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error caching search results", e);
        }
    }
    
    /**
     * 获取剧集列表缓存
     * 
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 缓存的剧集列表，如果没有缓存或缓存过期则返回null
     */
    public List<Movie.Episode> getCachedEpisodes(String baseUrl, String playUrl) {
        String cacheKey = "episodes_" + baseUrl + "_" + playUrl;
        
        // 先检查内存缓存
        CacheEntry memoryEntry = memoryCache.get(cacheKey);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            logger.info("Found episodes in memory cache for key: " + cacheKey);
            logger.info("Cache will expire at: " + memoryEntry.getExpireTimeString());
            return (List<Movie.Episode>) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getEpisodesCacheFilePath(cacheKey);
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile);
                CacheFileContent<List<Movie.Episode>> cacheFileContent = objectMapper.readValue(json, 
                    new TypeReference<CacheFileContent<List<Movie.Episode>>>() {});
                
                // 检查是否过期
                if (!cacheFileContent.isExpired()) {
                    List<Movie.Episode> episodes = cacheFileContent.getData();
                    
                    // 更新内存缓存
                    memoryCache.put(cacheKey, new CacheEntry(episodes));
                    
                    logger.info("Loaded episodes from file cache for key: " + cacheKey);
                    logger.info("Cache will expire at: " + cacheFileContent.getExpireTimeString());
                    return episodes;
                } else {
                    logger.info("File cache expired at: " + cacheFileContent.getExpireTimeString());
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading episodes from cache", e);
        }
        
        return null;
    }
    
    /**
     * 缓存剧集列表
     * 
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @param episodes 剧集列表
     */
    public void cacheEpisodes(String baseUrl, String playUrl, List<Movie.Episode> episodes) {
        String cacheKey = "episodes_" + baseUrl + "_" + playUrl;
        
        try {
            // 更新内存缓存
            CacheEntry cacheEntry = new CacheEntry(episodes);
            memoryCache.put(cacheKey, cacheEntry);
            
            // 更新文件缓存
            Path cacheFile = getEpisodesCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            // 创建带过期时间的缓存内容
            CacheFileContent<List<Movie.Episode>> cacheFileContent = new CacheFileContent<>(episodes, cacheEntry.getExpireTime());
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
            
            logger.info("Cached episodes for key: " + cacheKey);
            logger.info("Cache will expire at: " + cacheEntry.getExpireTimeString());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error caching episodes", e);
        }
    }
    
    /**
     * 获取M3U8 URL缓存
     * 
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集播放地址
     * @return 缓存的M3U8 URL，如果没有缓存或缓存过期则返回null
     */
    public String getCachedM3u8Url(String baseUrl, String episodeUrl) {
        String cacheKey = "m3u8_" + baseUrl + "_" + episodeUrl;
        
        // 先检查内存缓存
        CacheEntry memoryEntry = memoryCache.get(cacheKey);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            logger.info("Found M3U8 URL in memory cache for key: " + cacheKey);
            logger.info("Cache will expire at: " + memoryEntry.getExpireTimeString());
            return (String) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getM3u8CacheFilePath(cacheKey);
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile);
                CacheFileContent<String> cacheFileContent = objectMapper.readValue(json, 
                    new TypeReference<CacheFileContent<String>>() {});
                
                // 检查是否过期
                if (!cacheFileContent.isExpired()) {
                    String m3u8Url = cacheFileContent.getData();
                    
                    // 更新内存缓存
                    memoryCache.put(cacheKey, new CacheEntry(m3u8Url));
                    
                    logger.info("Loaded M3U8 URL from file cache for key: " + cacheKey);
                    logger.info("Cache will expire at: " + cacheFileContent.getExpireTimeString());
                    return m3u8Url;
                } else {
                    logger.info("File cache expired at: " + cacheFileContent.getExpireTimeString());
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading M3U8 URL from cache", e);
        }
        
        return null;
    }
    
    /**
     * 缓存M3U8 URL
     * 
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集播放地址
     * @param m3u8Url M3U8 URL
     */
    public void cacheM3u8Url(String baseUrl, String episodeUrl, String m3u8Url) {
        String cacheKey = "m3u8_" + baseUrl + "_" + episodeUrl;
        
        try {
            // 更新内存缓存
            CacheEntry cacheEntry = new CacheEntry(m3u8Url);
            memoryCache.put(cacheKey, cacheEntry);
            
            // 更新文件缓存
            Path cacheFile = getM3u8CacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            // 创建带过期时间的缓存内容
            CacheFileContent<String> cacheFileContent = new CacheFileContent<>(m3u8Url, cacheEntry.getExpireTime());
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
            
            logger.info("Cached M3U8 URL for key: " + cacheKey);
            logger.info("Cache will expire at: " + cacheEntry.getExpireTimeString());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error caching M3U8 URL", e);
        }
    }
    
    /**
     * 缓存文件内容包装类
     */
    private static class CacheFileContent<T> {
        private final T data;
        private final long expireTime;
        
        public CacheFileContent(T data, long expireTime) {
            this.data = data;
            this.expireTime = expireTime;
        }
        
        public T getData() {
            return data;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        public String getExpireTimeString() {
            return LocalDateTime.ofEpochSecond(expireTime/1000, 0, java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分ss秒"));
        }
    }
    
    /**
     * 获取搜索缓存文件路径
     * 
     * @param cacheKey 缓存键
     * @return 缓存文件路径
     */
    private Path getSearchCacheFilePath(String cacheKey) {
        // 在Windows系统中，文件名不能包含以下字符: < > : " / \ | ? *
        // 将这些字符替换为下划线
        String safeCacheKey = cacheKey.replaceAll("[<>:\"/\\\\|?*]", "_");
        return Paths.get(SEARCH_CACHE_DIR, safeCacheKey + ".cache");
    }
    
    /**
     * 获取剧集缓存文件路径
     * 
     * @param cacheKey 缓存键
     * @return 缓存文件路径
     */
    private Path getEpisodesCacheFilePath(String cacheKey) {
        // 在Windows系统中，文件名不能包含以下字符: < > : " / \ | ? *
        // 将这些字符替换为下划线
        String safeCacheKey = cacheKey.replaceAll("[<>:\"/\\\\|?*]", "_");
        return Paths.get(EPISODES_CACHE_DIR, safeCacheKey + ".cache");
    }
    
    /**
     * 获取M3U8缓存文件路径
     * 
     * @param cacheKey 缓存键
     * @return 缓存文件路径
     */
    private Path getM3u8CacheFilePath(String cacheKey) {
        // 在Windows系统中，文件名不能包含以下字符: < > : " / \ | ? *
        // 将这些字符替换为下划线
        String safeCacheKey = cacheKey.replaceAll("[<>:\"/\\\\|?*]", "_");
        return Paths.get(M3U8_CACHE_DIR, safeCacheKey + ".cache");
    }
    
    /**
     * 检查文件是否过期
     * 
     * @param file 文件路径
     * @return 如果文件过期返回true，否则返回false
     * @throws IOException 如果发生IO错误
     */
    private boolean isFileExpired(Path file) throws IOException {
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        return System.currentTimeMillis() - lastModified > CACHE_EXPIRE_TIME;
    }
    
    /**
     * 清理过期的缓存文件
     */
    public void cleanupExpiredCache() {
        cleanupExpiredCacheDir(SEARCH_CACHE_DIR);
        cleanupExpiredCacheDir(EPISODES_CACHE_DIR);
        cleanupExpiredCacheDir(M3U8_CACHE_DIR);
    }
    
    /**
     * 清理指定目录下的过期缓存文件
     * 
     * @param cacheDir 缓存目录
     */
    private void cleanupExpiredCacheDir(String cacheDir) {
        try {
            Path dirPath = Paths.get(cacheDir);
            if (!Files.exists(dirPath)) {
                return;
            }
            
            Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        String json = Files.readString(path);
                        CacheFileContent cacheFileContent = objectMapper.readValue(json, CacheFileContent.class);
                        return cacheFileContent.isExpired();
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Error checking file expiration", e);
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        logger.info("Deleted expired cache file: " + path);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Error deleting expired cache file: " + path, e);
                    }
                });
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error during cache cleanup for directory: " + cacheDir, e);
        }
    }
}