package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 简化的缓存管理器
 * 
 * 用于管理电影数据的文件缓存，避免重复爬取相同数据
 * 简化版实现，仅保留核心功能
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
    
    // 内存缓存清理间隔（毫秒）- 默认30分钟
    private static final long CACHE_CLEANUP_INTERVAL = 30 * 60 * 1000;
    
    // ObjectMapper用于序列化和反序列化
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 内存缓存，避免频繁读取文件
    private final ConcurrentHashMap<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    
    // 定时清理服务
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public CacheManager() {
        // 启动定时清理任务
        startCleanupTask();
    }
    
    /**
     * 启动定时清理任务
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpiredEntries, 
                                              CACHE_CLEANUP_INTERVAL, 
                                              CACHE_CLEANUP_INTERVAL, 
                                              TimeUnit.MILLISECONDS);
    }
    
    /**
     * 清理过期的内存缓存条目
     */
    private void cleanupExpiredEntries() {
        int cleanedCount = 0;
        
        for (String key : memoryCache.keySet()) {
            CacheEntry entry = memoryCache.get(key);
            if (entry != null && entry.isExpired()) {
                memoryCache.remove(key);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("Cache cleanup completed. Removed " + cleanedCount + " expired entries.");
        }
    }
    
    /**
     * 关闭缓存管理器，清理资源
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        memoryCache.clear();
    }
    
    /**
     * 缓存条目内部类
     */
    private static class CacheEntry {
        private final Object data;
        private final long expireTime;
        
        public CacheEntry(Object data) {
            this.data = data;
            this.expireTime = System.currentTimeMillis() + CACHE_EXPIRE_TIME;
        }
        
        public Object getData() {
            return data;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
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
            return (List<Movie>) memoryEntry.getData();
        }
        
        // 检查文件缓存
        try {
            Path cacheFile = getSearchCacheFilePath(cacheKey);
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile);
                CacheFileContent<List<Movie>> cacheFileContent = objectMapper.readValue(json, 
                    new TypeReference<CacheFileContent<List<Movie>>>() {});
                
                // 检查是否过期
                if (!cacheFileContent.isExpired()) {
                    List<Movie> movies = cacheFileContent.getData();
                    
                    // 更新内存缓存
                    memoryCache.put(cacheKey, new CacheEntry(movies));
                    
                    return movies;
                }
            }
        } catch (IOException e) {
            logger.warning("Error reading search results from cache: " + e.getMessage());
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
            CacheEntry cacheEntry = new CacheEntry(movies);
            memoryCache.put(cacheKey, cacheEntry);
            
            // 更新文件缓存
            Path cacheFile = getSearchCacheFilePath(cacheKey);
            Files.createDirectories(cacheFile.getParent());
            
            // 创建带过期时间的缓存内容
            CacheFileContent<List<Movie>> cacheFileContent = new CacheFileContent<>(movies, cacheEntry.expireTime);
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
        } catch (IOException e) {
            logger.warning("Error caching search results: " + e.getMessage());
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
                    
                    return episodes;
                }
            }
        } catch (IOException e) {
            logger.warning("Error reading episodes from cache: " + e.getMessage());
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
            CacheFileContent<List<Movie.Episode>> cacheFileContent = new CacheFileContent<>(episodes, cacheEntry.expireTime);
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
        } catch (IOException e) {
            logger.warning("Error caching episodes: " + e.getMessage());
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
                    
                    return m3u8Url;
                }
            }
        } catch (IOException e) {
            logger.warning("Error reading M3U8 URL from cache: " + e.getMessage());
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
            CacheFileContent<String> cacheFileContent = new CacheFileContent<>(m3u8Url, cacheEntry.expireTime);
            String json = objectMapper.writeValueAsString(cacheFileContent);
            Files.writeString(cacheFile, json);
        } catch (IOException e) {
            logger.warning("Error caching M3U8 URL: " + e.getMessage());
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
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
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
}