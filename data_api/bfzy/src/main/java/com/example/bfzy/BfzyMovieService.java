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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        
        // 如果返回空内容则返回空列表
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        // 解析JSON并只提取posts字段中的数据
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonArray postsArray = jsonObject.getAsJsonArray("posts");
        
        // 创建电影列表
        List<Movie> movies = new ArrayList<>();

        //遍历
        for (JsonElement postElement : postsArray) {
            System.out.println("========================================");
            //电影名称就是vod_name字段
            String name = postElement.getAsJsonObject().get("vod_name").getAsString();
            //海报信息是vod_pic字段
            String poster = postElement.getAsJsonObject().get("vod_pic").getAsString();
            //播放地址是vod_play_url字段
            String playUrl = postElement.getAsJsonObject().get("vod_play_url").getAsString();
            //简介是vod_content字段
            String description = postElement.getAsJsonObject().get("vod_content").getAsString();
            System.out.println("name:" + name);
            System.out.println("poster:" + poster);
            System.out.println("playUrl:" + playUrl);
            System.out.println("description:" + description);
            
            // 处理Unicode转义字符
            name = unescapeUnicode(name);
            description = unescapeUnicode(description);
            
            // 创建Movie对象并设置属性
            Movie movie = new Movie();
            movie.setName(name);
            movie.setPoster(poster);
            movie.setPlayUrl(playUrl);
            movie.setDescription(description);
            
            // 添加到列表
            movies.add(movie);
        }

        return movies;
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
        //playUrl按照#分割
        String[] playUrls = playUrl.split("#");
        
        // 创建剧集列表
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (String playUrl1 : playUrls) {
            //以$分割，0为title，1为episodeUrl
            String[] playUrl2 = playUrl1.split("\\$");
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle(playUrl2[0]);
            episode.setEpisodeUrl(playUrl2[1]);

            // 添加到剧集列表
            episodes.add(episode);
        }
        
        // 返回剧集列表
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
        // 对episodeUrl进行编码处理，将中文部分重新编码
        String encodedEpisodeUrl = encodeChineseToUnicode(episodeUrl);
        return encodedEpisodeUrl;
    }
    
    /**
     * 将episodeUrl编码为URL安全格式
     * 
     * @param episodeUrl 需要编码的episodeUrl
     * @return 编码后的episodeUrl
     */
    private String encodeEpisodeUrl(String episodeUrl) {
        try {
            // 如果episodeUrl已经是以/开头的绝对路径，则直接返回
            if (episodeUrl.startsWith("/")) {
                return episodeUrl;
            }
            
            // 对URL中的中文部分进行编码
            return "/" + URLEncoder.encode(episodeUrl, StandardCharsets.UTF_8.toString())
                    .replaceAll("%2F", "/"); // 保留URL中的斜杠
        } catch (Exception e) {
            e.printStackTrace();
            return "/" + episodeUrl; // 出错时添加前缀"/"并返回
        }
    }
    
    /**
     * 将字符串中的中文字符编码为Unicode格式
     * 
     * @param input 包含中文的字符串
     * @return 编码后的字符串
     */
    private String encodeChineseToUnicode(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        StringBuilder result = new StringBuilder();
        for (char ch : input.toCharArray()) {
            // 判断是否为中文字符
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                // 将中文字符转换为Unicode编码格式
                result.append(String.format("\\u%04X", (int) ch));
            } else {
                // 非中文字符直接添加
                result.append(ch);
            }
        }
        return result.toString();
    }
}