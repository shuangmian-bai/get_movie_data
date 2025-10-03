package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存管理器测试类
 * 
 * 分别测试搜索缓存、剧集缓存和M3U8链接缓存功能
 */
public class CacheManagerTest {

    private CacheManager cacheManager;
    private static final String TEST_BASE_URL = "https://test.example.com";
    private static final String TEST_KEYWORD = "测试电影";
    private static final String TEST_PLAY_URL = "https://test.example.com/movie/1";
    private static final String TEST_EPISODE_URL = "https://test.example.com/episode/1";
    private static final String TEST_M3U8_URL = "https://test.example.com/video/1.m3u8";
    private static boolean keepCacheFiles = false; // 控制是否保留缓存文件的标志

    @BeforeEach
    public void setUp() {
        cacheManager = new CacheManager();
        
        // 清理之前的缓存文件（仅在需要时）
        if (!keepCacheFiles) {
            deleteCacheDirectories();
        }
    }

    @AfterEach
    public void tearDown() {
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
        
        // 清理缓存文件（仅在需要时）
        if (!keepCacheFiles) {
            deleteCacheDirectories();
        }
    }

    private void deleteCacheDirectories() {
        deleteDirectory("cache/search");
        deleteDirectory("cache/episodes");
        deleteDirectory("cache/m3u8");
    }

    private void deleteDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            directory.delete();
        }
    }

    /**
     * 测试搜索结果缓存功能
     */
    @Test
    public void testSearchCache() {
        // 准备测试数据
        List<Movie> movies = createTestMovies();
        
        // 缓存搜索结果
        cacheManager.cacheSearchResults(TEST_BASE_URL, TEST_KEYWORD, movies);
        
        // 验证缓存文件已创建
        assertTrue(Files.exists(Paths.get("cache/search")), "搜索缓存目录应该存在");
        
        // 从缓存中获取数据
        List<Movie> cachedMovies = cacheManager.getCachedSearchResults(TEST_BASE_URL, TEST_KEYWORD);
        
        // 验证缓存数据正确性
        assertNotNull(cachedMovies, "缓存的电影列表不应为null");
        assertEquals(movies.size(), cachedMovies.size(), "缓存的电影数量应该匹配");
        assertEquals(movies.get(0).getName(), cachedMovies.get(0).getName(), "电影名称应该匹配");
        assertEquals(movies.get(0).getDescription(), cachedMovies.get(0).getDescription(), "电影描述应该匹配");
    }

    /**
     * 测试剧集列表缓存功能
     */
    @Test
    public void testEpisodesCache() {
        // 准备测试数据
        List<Movie.Episode> episodes = createTestEpisodes();
        
        // 缓存剧集列表
        cacheManager.cacheEpisodes(TEST_BASE_URL, TEST_PLAY_URL, episodes);
        
        // 验证缓存文件已创建
        assertTrue(Files.exists(Paths.get("cache/episodes")), "剧集缓存目录应该存在");
        
        // 从缓存中获取数据
        List<Movie.Episode> cachedEpisodes = cacheManager.getCachedEpisodes(TEST_BASE_URL, TEST_PLAY_URL);
        
        // 验证缓存数据正确性
        assertNotNull(cachedEpisodes, "缓存的剧集列表不应为null");
        assertEquals(episodes.size(), cachedEpisodes.size(), "缓存的剧集数量应该匹配");
        assertEquals(episodes.get(0).getTitle(), cachedEpisodes.get(0).getTitle(), "剧集标题应该匹配");
        assertEquals(episodes.get(0).getEpisodeUrl(), cachedEpisodes.get(0).getEpisodeUrl(), "剧集URL应该匹配");
    }

    /**
     * 测试M3U8链接缓存功能
     */
    @Test
    public void testM3u8UrlCache() {
        // 缓存M3U8链接
        cacheManager.cacheM3u8Url(TEST_BASE_URL, TEST_EPISODE_URL, TEST_M3U8_URL);
        
        // 验证缓存文件已创建
        assertTrue(Files.exists(Paths.get("cache/m3u8")), "M3U8缓存目录应该存在");
        
        // 从缓存中获取数据
        String cachedM3u8Url = cacheManager.getCachedM3u8Url(TEST_BASE_URL, TEST_EPISODE_URL);
        
        // 验证缓存数据正确性
        assertNotNull(cachedM3u8Url, "缓存的M3U8 URL不应为null");
        assertEquals(TEST_M3U8_URL, cachedM3u8Url, "缓存的M3U8 URL应该匹配");
    }

    // 用于手动测试时保留缓存文件的方法
    public static void setKeepCacheFiles(boolean keep) {
        keepCacheFiles = keep;
    }

    private List<Movie> createTestMovies() {
        List<Movie> movies = new ArrayList<>();
        Movie movie = new Movie();
        movie.setName("测试电影");
        movie.setDescription("这是一部测试电影");
        movie.setPlayUrl(TEST_PLAY_URL);
        movie.setBaseUrl(TEST_BASE_URL);
        movies.add(movie);
        return movies;
    }

    private List<Movie.Episode> createTestEpisodes() {
        List<Movie.Episode> episodes = new ArrayList<>();
        Movie.Episode episode = new Movie.Episode();
        episode.setTitle("测试剧集");
        episode.setEpisodeUrl(TEST_EPISODE_URL);
        episodes.add(episode);
        return episodes;
    }
}