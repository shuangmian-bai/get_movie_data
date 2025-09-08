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
        // 搜索接口:
        //https://www.857kan.com/search/{keyword}-------------.html
        // 构建搜索url
        String searchUrl = baseUrl + "/search/" + keyword + "-------------.html";

        // 发送get请求
        String html = sendGetRequest(searchUrl);
        // 创建jsoup对象
        Document doc = Jsoup.parse(html);

        // 找到body > div.wrap > div > div > ul > li:nth-child(9) > a,获取href
        String href = doc.select("body > div.wrap > div > div > ul > li:nth-child(9) > a").attr("href");
        // 以-分割
        String[] split = href.split("-");
        // 查找其中的整数值
        int totalPages = 0;
        for (String part : split) {
            try {
                totalPages = Integer.parseInt(part);
                // 找到第一个整数就跳出循环
                break;
            } catch (NumberFormatException e) {
                // 不是整数，继续查找
                continue;
            }
        }
        
        System.out.println("找到"+totalPages+"页数据");
        
        // 使用线程池并发获取所有页面数据
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<Movie>>> futures = new ArrayList<>();
        
        // 提交所有页面的获取任务
        for (int page = 1; page <= totalPages; page++) {
            final int pageNum = page;
            Future<List<Movie>> future = executor.submit(() -> getMoviesByPage(baseUrl, keyword, pageNum));
            futures.add(future);
        }
        
        // 收集所有结果
        List<Movie> allMovies = new ArrayList<>();
        for (Future<List<Movie>> future : futures) {
            try {
                List<Movie> movies = future.get(10, TimeUnit.SECONDS); // 设置超时时间
                allMovies.addAll(movies);
            } catch (Exception e) {
                System.err.println("获取页面数据时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 关闭线程池
        executor.shutdown();
        
        return allMovies;
    }

    //定义一个函数，传入搜索关键词和页码，返回这一页的数据
    public List<Movie> getMoviesByPage(String baseUrl, String keyword, int page) {
        //https://www.857kan.com/search/{keyword}----------{page}---.html
        //构建搜索url
        String searchUrl = baseUrl + "/search/" + keyword + "----------" + page + "---.html";
        //发送get请求
        String html = sendGetRequest(searchUrl);
        Document doc = Jsoup.parse(html);
        //搜索#searchList > li:nth-child(1)
        Elements elements = doc.select("#searchList > li");
        
        // 创建电影列表
        List<Movie> movies = new ArrayList<>();
        
        for (Element element : elements) {
            //找到li > div.detail > h4 > a
            String name = element.select("div.detail > h4 > a").text();
            //找到li > div.detail > p.hidden-xs作为描述
            String description = element.select("div.detail > p.hidden-xs").text();
            //找到 li > div.thumb > a ,定位data-original元素为海报内容
            String poster = element.select("div.thumb > a").attr("data-original");
            //找到li > div.thumb > a的href
            String playUrl = baseUrl + element.select("div.thumb > a").attr("href");
            
            // 创建Movie对象并设置属性
            Movie movie = new Movie();
            movie.setName(name);
            movie.setDescription(description);
            movie.setPoster(poster);
            movie.setPlayUrl(playUrl);

            System.out.println("========================");
            System.out.println( name);
            System.out.println( description);
            System.out.println( poster);
            System.out.println( playUrl);

            // 添加到电影列表
            movies.add(movie);
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

        //发送GET请求
        String response = sendGetRequest(playUrl);
        //创建doc对象
        Document doc = Jsoup.parse(response);
        
        // 创建剧集列表
        List<Movie.Episode> episodes = new ArrayList<>();
        
        //找到#playlist1 > ul > li里面的全部a标签并且循环
        for (Element element : doc.select("#playlist1 > ul > li > a")) {
            //a标签的文本为title，a标签的href为episodeUrl，需要设置变量
            String title = element.text();
            String episodeUrl = baseUrl + element.attr("href");
            
            // 创建Episode对象并设置属性
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle(title);
            episode.setEpisodeUrl(episodeUrl);
            
            // 添加到剧集列表
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
        // 发送GET请求
        String response = sendGetRequest(episodeUrl);
        System.out.println( response);
        return null;
    }
}