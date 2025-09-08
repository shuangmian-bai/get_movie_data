package com.example.bfzy;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * bfzy.tv电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author bfzy team
 * @version 1.0.0
 */
public class BfzyMovieService {
    
    // 创建OkHttp客户端实例
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    // 线程池大小控制变量
    private static final int THREAD_POOL_SIZE = 10;
    
    /**
     * 处理Unicode转义字符
     * 
     * @param input 包含Unicode转义字符的字符串
     * @return 解码后的字符串
     */
    private String unescapeUnicode(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return StringEscapeUtils.unescapeJava(input);
    }
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        //http://search.bfzyapi.com/json-api/?dname=baofeng&key={keyword}&count=20
        String url = "http://search.bfzyapi.com/" + "json-api/?dname=baofeng&key=" + keyword + "&count=20";
        //发送请求,获取json
        String json = sendGetRequest(url);


        return null;
    }

    /**
     * 使用多线程获取所有页面数据
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @param pageCount 总页数
     * @return 所有页面的电影列表
     */
    private List<Movie> getAllPageData(String baseUrl, String keyword, int pageCount) {
        // 创建固定大小的线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<Movie>>> futures = new ArrayList<>();
        
        try {
            // 提交所有任务
            for (int i = 1; i <= pageCount; i++) {
                final int page = i;
                Future<List<Movie>> future = executor.submit(() -> getPageData(baseUrl, keyword, page));
                futures.add(future);
            }
            
            // 收集所有结果
            List<Movie> allMovies = new ArrayList<>();
            for (Future<List<Movie>> future : futures) {
                try {
                    List<Movie> pageMovies = future.get();
                    allMovies.addAll(pageMovies);
                } catch (Exception e) {
                    System.err.println("获取页面数据时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return allMovies;
        } finally {
            // 关闭线程池
            executor.shutdown();
            try {
                // 等待所有任务完成
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取一页数据
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @param page 页码
     * @return 这一页的电影列表
     */
    private List<Movie> getPageData(String baseUrl, String keyword, int page) {
        // https://bfzy.tv/vodsearch/{keyword}----------{page}---.html
        // 构建搜索url
        String searchUrl = baseUrl + "/vodsearch/" + keyword + "----------" + page + "---.html";
        String html = sendGetRequest(searchUrl);

        // 创建jsoup对象
        Document doc = Jsoup.parse(html);
        // 查询类为.module-item
        Elements movieItems = doc.select(".module-item");
        
        // 创建Movie列表
        List<Movie> movies = new ArrayList<>();

        for (Element item : movieItems) {
            System.out.println("==================================");
            
            // 选择电影名称
            String name = unescapeUnicode(item.select(".module-poster-item-title").text());
            
            // 选择剧情描述
            String description = unescapeUnicode(item.select(".module-item-note").first().text());
            
            // 获取播放链接
            String playUrl = baseUrl + item.select(".module-poster-item-link").attr("href");
            
            // 获取海报信息
            String poster = item.select(".lazy.lazyload").attr("data-original");
            
            System.out.println("name: " + name);
            System.out.println("description: " + description);
            System.out.println("playUrl: " + playUrl);
            System.out.println("poster: " + poster);

            // 创建Movie对象并设置属性
            Movie movie = new Movie();
            movie.setName(name);
            movie.setDescription(description);
            movie.setPlayUrl(playUrl);
            movie.setPoster(poster);
            
            // 添加到列表
            movies.add(movie);
        }

        // 返回Movie列表
        return movies;
    }

    private String sendGetRequest(String urlString) {
        try {
            // 创建请求
            Request request = new Request.Builder()
                    .url(urlString)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .addHeader("Connection", "keep-alive")
                    .build();

            // 执行请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                
                // 获取响应体
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    return responseBody.string();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取指定电影的所有剧集
     * 
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 剧集列表
     */
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        String html = sendGetRequest(playUrl);
        // 创建jsoup对象
        Document doc = Jsoup.parse(html);
        
        // 选择剧集列表
        Elements elements = doc.select(".module-play-list-content a");
        
        // 创建剧集列表
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (Element element : elements) {
            String title = unescapeUnicode(element.text());
            String episodeUrl = baseUrl + element.attr("href");
            System.out.println("title: " + title);
            System.out.println("episodeUrl: " + episodeUrl);
            
            // 构造剧集对象并添加到列表中
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle(title);
            episode.setEpisodeUrl(episodeUrl);
            episodes.add(episode);
        }

        return episodes;
    }

    /**
     * 获取指定剧集的M3U8播放地址
     * 
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集播放地址
     * @return M3U8播放地址
     */
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        String html = sendGetRequest(episodeUrl);
        
        // 使用正则表达式匹配m3u8地址
        Pattern pattern = Pattern.compile("https?://[^\"]*\\.m3u8[^\"]*");
        Matcher matcher = pattern.matcher(html);
        String m3u8Url = null;
        if (matcher.find()) {
            m3u8Url = matcher.group();
            System.out.println("m3u8Url: " + m3u8Url);
            return m3u8Url;
        }

        return m3u8Url;
    }
}