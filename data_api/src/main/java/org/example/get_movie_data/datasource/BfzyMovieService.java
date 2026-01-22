package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.util.HttpClientUtil;
import org.example.get_movie_data.util.HtmlParserUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.get_movie_data.model.Movie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bfzy.tv电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author bfzy team
 * @version 1.0.0
 */
@DataSource(
    id = "bfzy",
    name = "暴风影音数据源",
    description = "从暴风影音网站获取电影数据",
    baseUrl = "https://bfzy.tv",
    version = "1.0.0"
)
public class BfzyMovieService implements MovieService {
    
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
        
        // 使用正则表达式处理Unicode转义序列
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String unicodeHex = matcher.group(1);
            int unicodeValue = Integer.parseInt(unicodeHex, 16);
            char unicodeChar = (char) unicodeValue;
            matcher.appendReplacement(result, Character.toString(unicodeChar));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        //http://search.bfzyapi.com/json-api/?dname=baofeng&key={keyword}&count=20
        String url = "http://search.bfzyapi.com/" + "json-api/?dname=baofeng&key=" + keyword + "&count=20";
        //发送请求,获取json
        String json = HttpClientUtil.sendGetRequest(url);
        
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
            //电影名称就是vod_name字段
            String name = postElement.getAsJsonObject().get("vod_name").getAsString();
            //海报信息是vod_pic字段
            String poster = postElement.getAsJsonObject().get("vod_pic").getAsString();
            //播放地址是vod_play_url字段
            String playUrl = postElement.getAsJsonObject().get("vod_play_url").getAsString();
            //简介是vod_content字段
            String description = postElement.getAsJsonObject().get("vod_content").getAsString();

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
        String html = HttpClientUtil.sendGetRequest(searchUrl);

        // 创建jsoup对象
        Document doc = HtmlParserUtil.parse(html);
        // 查询类为.module-item
        Elements movieItems = HtmlParserUtil.select(doc, ".module-item");
        
        // 创建Movie列表
        List<Movie> movies = new ArrayList<>();

        for (Element item : movieItems) {
            // 选择电影名称
            String name = unescapeUnicode(item.select(".module-poster-item-title").text());
            
            // 选择剧情描述
            String description = unescapeUnicode(item.select(".module-item-note").first().text());
            
            // 获取播放链接
            String playUrl = baseUrl + item.select(".module-poster-item-link").attr("href");
            
            // 获取海报信息
            String poster = item.select(".lazy.lazyload").attr("data-original");

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

    /**
     * 获取指定电影的所有剧集
     * 
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 剧集列表
     */
    @Override
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
    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 直接返回episodeUrl，不进行额外编码处理
        return episodeUrl;
    }

    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        if ("bfzy".equals(datasourceId)) {
            return this;
        }
        return null;
    }
}