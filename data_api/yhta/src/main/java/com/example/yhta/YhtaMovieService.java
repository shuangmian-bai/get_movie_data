package com.example.yhta;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * YHTA电影服务实现类
 * 
 * 这是一个外部数据源的实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author yhta development team
 * @version 1.0.0
 */
public class YhtaMovieService {
    
    // 线程池大小控制变量
    private static final int THREAD_POOL_SIZE = 10;
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        // 搜索接口: https://www.yhta.cc/vodsearch/-------------.html?wd={keyword}
        // 构建搜索url
        String searchUrl = baseUrl + "/vodsearch/-------------.html?wd=" + keyword;

        // 发送get请求
        String html = sendGetRequest(searchUrl);
        // 创建jsoup对象
        Document doc = Jsoup.parse(html);

        // 获取搜索结果项
        Elements movieItems = doc.select(".module-item");
        
        // 创建Movie列表
        List<Movie> movies = new ArrayList<>();

        for (Element item : movieItems) {
            try {
                // 提取电影信息
                String name = item.select(".video-name a").attr("title");
                String playUrl = baseUrl + item.select(".video-name a").attr("href");
                String poster = item.select(".module-item-pic img").attr("data-src");
                String description = item.select(".video-info-item.desc").text();
                
                // 创建Movie对象并设置属性
                Movie movie = new Movie();
                movie.setName(name);
                movie.setDescription(description);
                movie.setPlayUrl(playUrl);
                movie.setPoster(poster);
                
                // 添加到列表
                movies.add(movie);
            } catch (Exception e) {
                System.err.println("解析电影项时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return movies;
    }

    private String sendGetRequest(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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
        
        // 选择剧集元素
        Elements elements = doc.select(".module-blocklist a");
        
        // 创建剧集列表
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (Element element : elements) {
            String title = element.select("span").text();
            if (title == null || title.isEmpty()) {
                title = element.text();
            }
            
            String episodeUrl = baseUrl + element.attr("href");
            
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
        Pattern pattern = Pattern.compile("https?://[^\"]*m3u8[^\"]*");
        Matcher matcher = pattern.matcher(html);
        String m3u8Url = null;
        if (matcher.find()) {
            m3u8Url = matcher.group();
            return m3u8Url;
        }

        return m3u8Url;
    }
}