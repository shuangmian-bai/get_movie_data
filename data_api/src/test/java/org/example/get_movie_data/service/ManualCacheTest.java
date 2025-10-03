package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动测试缓存功能的类
 * 
 * 用于验证三种缓存文件是否被正确创建
 */
public class ManualCacheTest {
    
    public static void main(String[] args) {
        // 保留缓存文件
        CacheManagerTest.setKeepCacheFiles(true);
        
        CacheManager cacheManager = new CacheManager();
        
        try {
            // 测试搜索缓存
            testSearchCache(cacheManager);
            
            // 测试剧集缓存
            testEpisodesCache(cacheManager);
            
            // 测试M3U8缓存
            testM3u8Cache(cacheManager);
            
            System.out.println("所有缓存测试完成，请检查 cache 目录下的文件");
            
        } finally {
            cacheManager.shutdown();
        }
    }
    
    private static void testSearchCache(CacheManager cacheManager) {
        System.out.println("测试搜索缓存...");
        
        // 准备测试数据
        List<Movie> movies = new ArrayList<>();
        Movie movie = new Movie();
        movie.setName("测试电影");
        movie.setDescription("这是一部测试电影");
        movie.setPlayUrl("https://test.example.com/movie/1");
        movie.setBaseUrl("https://test.example.com");
        movies.add(movie);
        
        // 缓存搜索结果
        cacheManager.cacheSearchResults("https://test.example.com", "测试电影", movies);
        
        // 从缓存中获取数据
        List<Movie> cachedMovies = cacheManager.getCachedSearchResults("https://test.example.com", "测试电影");
        
        if (cachedMovies != null) {
            System.out.println("搜索缓存测试通过，缓存了 " + cachedMovies.size() + " 部电影");
        } else {
            System.out.println("搜索缓存测试失败");
        }
    }
    
    private static void testEpisodesCache(CacheManager cacheManager) {
        System.out.println("测试剧集缓存...");
        
        // 准备测试数据
        List<Movie.Episode> episodes = new ArrayList<>();
        Movie.Episode episode = new Movie.Episode();
        episode.setTitle("测试剧集");
        episode.setEpisodeUrl("https://test.example.com/episode/1");
        episodes.add(episode);
        
        // 缓存剧集列表
        cacheManager.cacheEpisodes("https://test.example.com", "https://test.example.com/movie/1", episodes);
        
        // 从缓存中获取数据
        List<Movie.Episode> cachedEpisodes = cacheManager.getCachedEpisodes("https://test.example.com", "https://test.example.com/movie/1");
        
        if (cachedEpisodes != null) {
            System.out.println("剧集缓存测试通过，缓存了 " + cachedEpisodes.size() + " 个剧集");
        } else {
            System.out.println("剧集缓存测试失败");
        }
    }
    
    private static void testM3u8Cache(CacheManager cacheManager) {
        System.out.println("测试M3U8缓存...");
        
        // 缓存M3U8链接
        String m3u8Url = "https://test.example.com/video/1.m3u8";
        cacheManager.cacheM3u8Url("https://test.example.com", "https://test.example.com/episode/1", m3u8Url);
        
        // 从缓存中获取数据
        String cachedM3u8Url = cacheManager.getCachedM3u8Url("https://test.example.com", "https://test.example.com/episode/1");
        
        if (cachedM3u8Url != null) {
            System.out.println("M3U8缓存测试通过，缓存的URL: " + cachedM3u8Url);
        } else {
            System.out.println("M3U8缓存测试失败");
        }
    }
}