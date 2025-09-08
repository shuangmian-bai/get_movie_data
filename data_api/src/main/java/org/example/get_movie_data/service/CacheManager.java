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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

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
    private static final String CACHE_DIR = "cache";
    
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
        
        public CacheEntry(Object data) {
            this.data = data;
            this.createTime = System.currentTimeMillis();
        }
        
        public Object getData() {
            return data;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > CACHE_EXPIRE_TIME;
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
        
        // 先检查内存缓存
        CacheEntry memoryEntry = memoryCache.get(cacheKey);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            logger.info("Found search results in memory cache for key: " + cacheKey);
            return (List<Movie>) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getCacheFilePath(cacheKey);
            if (Files.exists(cacheFile) && !isFileExpired(cacheFile)) {
                String json = Files.readString(cacheFile);
                List<Movie> movies = objectMapper.readValue(json, 
                    TypeFactory.defaultInstance().constructCollectionType(List.class, Movie.class));
                
                // 更新内存缓存
                memoryCache.put(cacheKey, new CacheEntry(movies));
                
                logger.info("Loaded search results from file cache for key: " + cacheKey);
                return movies;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading search results from cache", e);
        }
        
        return null;
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
            memoryCache.put(cacheKey, new CacheEntry(movies));
            
            // 更新文件缓存
            Path cacheFile = getCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            String json = objectMapper.writeValueAsString(movies);
            Files.writeString(cacheFile, json);
            
            logger.info("Cached search results for key: " + cacheKey);
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
            return (List<Movie.Episode>) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getCacheFilePath(cacheKey);
            if (Files.exists(cacheFile) && !isFileExpired(cacheFile)) {
                String json = Files.readString(cacheFile);
                List<Movie.Episode> episodes = objectMapper.readValue(json, 
                    new TypeReference<List<Movie.Episode>>() {});
                
                // 更新内存缓存
                memoryCache.put(cacheKey, new CacheEntry(episodes));
                
                logger.info("Loaded episodes from file cache for key: " + cacheKey);
                return episodes;
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
            memoryCache.put(cacheKey, new CacheEntry(episodes));
            
            // 更新文件缓存
            Path cacheFile = getCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            String json = objectMapper.writeValueAsString(episodes);
            Files.writeString(cacheFile, json);
            
            logger.info("Cached episodes for key: " + cacheKey);
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
            return (String) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getCacheFilePath(cacheKey);
            if (Files.exists(cacheFile) && !isFileExpired(cacheFile)) {
                String m3u8Url = Files.readString(cacheFile);
                
                // 更新内存缓存
                memoryCache.put(cacheKey, new CacheEntry(m3u8Url));
                
                logger.info("Loaded M3U8 URL from file cache for key: " + cacheKey);
                return m3u8Url;
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
            memoryCache.put(cacheKey, new CacheEntry(m3u8Url));
            
            // 更新文件缓存
            Path cacheFile = getCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            Files.writeString(cacheFile, m3u8Url);
            
            logger.info("Cached M3U8 URL for key: " + cacheKey);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error caching M3U8 URL", e);
        }
    }
    
    /**
     * 获取缓存文件路径
     * 
     * @param cacheKey 缓存键
     * @return 缓存文件路径
     */
    private Path getCacheFilePath(String cacheKey) {
        return Paths.get(CACHE_DIR, cacheKey + ".cache");
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
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                return;
            }
            
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return isFileExpired(path);
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
            logger.log(Level.WARNING, "Error during cache cleanup", e);
        }
    }
}