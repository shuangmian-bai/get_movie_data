package com.example.yuny;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 添加Jackson库相关导入
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 云云TV电影服务实现类
 * 
 * 实现了MovieService接口，提供对云云TV网站数据的抓取和处理功能
 * 
 * @author yuny development team
 * @version 1.0.0
 */
public class YunyMovieService implements MovieService {
    
    // 控制最大线程数量的变量
    private static final int MAX_THREAD_COUNT = 5;
    
    /**
     * 根据搜索关键词获取影视信息
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 影视信息列表
     */
    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        try {
            // 对关键词进行URL编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
            String searchUrl = baseUrl + "/videoSearch?key=" + encodedKeyword;
            System.out.println("正在请求URL: " + searchUrl);
            
            String html = HttpUtils.get(searchUrl);

            Document doc = Jsoup.parse(html);

            //获取#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div:nth-child(2) > div.search_result_info的文本存储为一个字符串
            String infoText = doc.select("#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div:nth-child(2) > div.search_result_info").text();
            infoText = infoText.split("部")[0];
            infoText = infoText.split("到")[1];
            //转换为整形，爬取到的影视总数量
            int totalMovies = Integer.parseInt(infoText);
            System.out.println("搜索结果共有" + totalMovies + "部");

            //获取#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div:nth-child(2) > div.search_result_list.flex.flex-wrap.gap-\[--fs-spacing\]下a标签的数量，存储为整型
            int movieCount = doc.select("div.search_result_list").select("a").size();
            System.out.println("一页有"+movieCount+"部影视");

            //计算有多少页数据
            int pageCount = (int) Math.ceil((double) totalMovies / movieCount);
            System.out.println("共有"+pageCount+"页数据");

            // 使用多线程爬取所有页面数据
            List<Movie> movies = fetchMoviesWithMultiThread(encodedKeyword, pageCount, baseUrl);
            
            // 去除重复的电影条目
            return removeDuplicateMovies(movies);


        }
        catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 返回空列表而不是null
        }
    }

    /**
     * 去除重复的电影条目
     * @param movies 电影列表
     * @return 去重后的电影列表
     */
    private List<Movie> removeDuplicateMovies(List<Movie> movies) {
        // 使用LinkedHashSet保持插入顺序并去重
        Set<String> seenNames = new LinkedHashSet<>();
        List<Movie> uniqueMovies = new ArrayList<>();
        
        for (Movie movie : movies) {
            // 清理数据格式问题
            cleanMovieData(movie);
            
            // 检查是否已存在相同名称的电影
            if (!seenNames.contains(movie.getName())) {
                seenNames.add(movie.getName());
                uniqueMovies.add(movie);
            }
        }
        
        return uniqueMovies;
    }
    
    /**
     * 清理电影数据格式问题
     * @param movie 电影对象
     */
    private void cleanMovieData(Movie movie) {
        // 去除名称、描述、海报链接中的多余引号
        if (movie.getName() != null) {
            movie.setName(movie.getName().replaceAll("^\"|\"$", ""));
        }
        
        if (movie.getDescription() != null) {
            movie.setDescription(movie.getDescription().replaceAll("^\"|\"$", ""));
        }
        
        if (movie.getPoster() != null) {
            movie.setPoster(movie.getPoster().replaceAll("^\"|\"$", ""));
        }
        
        // 确保海报链接是完整的URL
        if (movie.getPoster() != null && !movie.getPoster().startsWith("http")) {
            movie.setPoster("https://image.yunyf.com" + movie.getPoster());
        }
    }

    /**
     * 使用多线程方式爬取所有页面的电影数据
     * @param encodedKeyword 已编码的搜索关键词
     * @param pageCount 总页数
     * @param baseUrl 基础URL
     * @return 所有电影数据列表
     */
    private List<Movie> fetchMoviesWithMultiThread(String encodedKeyword, int pageCount, String baseUrl) {
        // 创建线程池，控制最大线程数量
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD_COUNT);
        List<Future<List<Movie>>> futures = new ArrayList<>();
        
        // 提交所有页面爬取任务
        for (int page = 1; page <= pageCount; page++) {
            final int currentPage = page;
            Future<List<Movie>> future = executor.submit(() -> {
                try {
                    return getMovies(encodedKeyword, currentPage, baseUrl);
                } catch (Exception e) {
                    System.err.println("爬取第" + currentPage + "页数据时出错: " + e.getMessage());
                    e.printStackTrace();
                    return new ArrayList<>();
                }
            });
            futures.add(future);
        }
        
        // 关闭线程池，不再接受新任务
        executor.shutdown();
        
        // 收集所有结果
        List<Movie> allMovies = new ArrayList<>();
        for (Future<List<Movie>> future : futures) {
            try {
                List<Movie> movies = future.get(30, TimeUnit.SECONDS); // 设置超时时间
                allMovies.addAll(movies);
            } catch (Exception e) {
                System.err.println("获取任务结果时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return allMovies;
    }

    //创建爬取一页数据函数，传入搜索关键词，页码和baseurl
    public List<Movie> getMovies(String encodedKeyword, int page, String baseUrl) throws JsonProcessingException {
        // 构造请求URL
        String url = baseUrl + "/videoSearch?key=" + encodedKeyword + "&current=" + page;
        System.out.println("正在请求URL: " + url);

        // 发送HTTP请求
        String html = HttpUtils.get(url);
        if (html == null || html.isEmpty()) {
            System.err.println("HTTP请求返回空内容");
            return new ArrayList<>();
        }

        Document doc = Jsoup.parse(html);
        Element nuxtDataElement = doc.selectFirst("#__NUXT_DATA__");

        List<Movie> movies = new ArrayList<>();

        //存储为变量
        String jsonData = nuxtDataElement.data().trim();
        System.out.println(jsonData);

        //分割，找到数据索引
        String data_info = jsonData.split("data")[2].split("}")[0].split(":")[1];
        //转换为整数
        int data = Integer.parseInt(data_info);

        //新建一个解析对象OuterArrayParser
        OuterArrayParser parser = new OuterArrayParser();
        List<String> dataArray = parser.parseOuterArray(jsonData);

        //找到dataArray的索引8的数据
        String dataArray_8 = dataArray.get(8);
        //解析
        data = Integer.parseInt(dataArray_8.split("records")[1].split(",")[0].split(":")[1]);

        String dataArray_9 = dataArray.get(9);
        List<String> dataArray2 = parser.parseOuterArray(dataArray_9);

        //遍历dataArray
        for (String dataArray_9_item : dataArray2) {
            //转换为int
            int dataArray_9_item_int = Integer.parseInt(dataArray_9_item);
            String datas = dataArray.get(dataArray_9_item_int);
            //datas是一个json，我要id，name，description，tags
            JsonNode jsonNode = new ObjectMapper().readTree(datas);
            String id = jsonNode.get("id").asText();
            String name = jsonNode.get("name").asText();
            String description = jsonNode.get("description").asText();
            String cover = jsonNode.get("cover").asText();

            id = dataArray.get(Integer.parseInt(id));
            name = dataArray.get(Integer.parseInt(name));
            description = dataArray.get(Integer.parseInt(description));
            cover = dataArray.get(Integer.parseInt(cover));
            
            // 创建Movie对象并设置属性
            Movie movie = new Movie();
            movie.setName(name);
            movie.setDescription(description);
            movie.setPoster(cover);
            // 设置播放地址，根据网站结构，应该是https://www.yuny.tv/videoDetail/8554
            String playUrl = baseUrl + "/videoDetail/" + id;
            
            System.out.println("===============================");
            System.out.println("id:"+id);
            System.out.println("name:"+name);
            System.out.println("description:"+description);
            System.out.println("cover:"+cover);
            System.out.println("playUrl:"+playUrl);
            movie.setPlayUrl(playUrl);
            
            // 添加到电影列表
            movies.add(movie);
        }

        return movies;
    }

    /**
     * 根据播放地址获取影视的全部集数和标题以及播放地址
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 影视剧集列表
     */
    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        try {
            //发送HTTP GET请求获取网页内容
            String html = HttpUtils.get(playUrl);
            //创建doc解析对象
            Document doc = Jsoup.parse(html);
            //搜索#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div.video-detail-main > div.episode_box_main > div并且遍历里面全部的a标签
            Elements elements = doc.select("#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div.video-detail-main > div.episode_box_main > div > a");
            
            List<Movie.Episode> episodes = new ArrayList<>();
            
            for (Element element : elements) {
                //文本为title，href为episodeUrl
                String title = element.text();
                String episodeUrl = baseUrl + element.attr("href");
                
                // 创建Episode对象并设置属性
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle(title);
                episode.setEpisodeUrl(episodeUrl);
                
                episodes.add(episode);
                
                System.out.println("==================================");
                System.out.println("title:"+title);
                System.out.println("episodeUrl:"+episodeUrl);
            }
            
            return episodes;
        } catch (Exception e) {
            e.printStackTrace();
            // 发生异常时返回空列表而不是null
            return new ArrayList<>();
        }
    }

    /**
     * 获取具体播放地址的m3u8
     * @param baseUrl 基础URL
     * @param episodeUrl 具体播放地址
     * @return m3u8地址
     */
    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        try {
            //发送HTTP GET请求获取网页内容
            String html = HttpUtils.get(episodeUrl);
            
            if (html == null || html.isEmpty()) {
                System.err.println("HTTP请求返回空内容");
                return "";
            }
            
            //利用正则表达式提取http开头.m3u8结尾的数据,并且保存字符串
            Pattern pattern = Pattern.compile("http[s]?://[\\w\\./\\-?&=]+\\.m3u8");
            Matcher matcher = pattern.matcher(html);
            
            // 检查是否有匹配的内容
            if (matcher.find()) {
                String m3u8Url = matcher.group();
                System.out.println("m3u8Url:"+m3u8Url);
                return m3u8Url;
            } else {
                System.err.println("未找到匹配的m3u8地址");
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}