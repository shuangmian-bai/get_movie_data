package com.example.chabeigu;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;


/**
 * 茶杯狐电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author chabeigu team
 * @version 1.0.0
 */
public class ChabeiguMovieService {
    
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
        //搜索接口https://www.chabeigu.com/index.php/vod/search.html?wd={keyword}
        //构建搜索url
        String searchUrl = baseUrl + "/index.php/vod/search.html?wd=" + keyword;

        //发送get请求
        String html = sendGetRequest(searchUrl);
        //创建jsoup对象
        Document doc = Jsoup.parse(html);

        //获取到有多少页数据
        Elements pageElements = doc.select("#page > a:nth-child(9)");
        System.out.println("pageElements : "+pageElements);
        int pageCount = 1; // 默认值改为1
        if (!pageElements.isEmpty()) {
            Element pageElement = pageElements.first();
            String href = pageElement.attr("href");
            // 从href中提取页码，例如 "/index.php/vod/search/page/页码/wd/搜索关键词.html"
            if (href != null && !href.isEmpty()) {
                try {
                    // 使用正则表达式提取页码
                    String[] parts = href.split("/");
                    if (parts.length > 0) {
                        //获取到字符串page所在的索引
                        int pageIndex = Arrays.asList(parts).indexOf("page");
                        pageCount = Integer.parseInt(parts[pageIndex+1]);
                    }
                } catch (NumberFormatException e) {
                    pageCount = 1; // 出错时默认为1
                    System.err.println("解析页码时出错: " + e.getMessage());
                }
            }
        } else {
            pageCount = 1; // 没有找到元素时默认为1
        }

        System.out.println("总页数: " + pageCount);

        // 使用多线程获取所有页面数据
        List<Movie> movies = getAllPageData(keyword, pageCount);

        return movies;
    }

    /**
     * 使用多线程获取所有页面数据
     * 
     * @param keyword 搜索关键词
     * @param pageCount 总页数
     * @return 所有页面的电影列表
     */
    private List<Movie> getAllPageData(String keyword, int pageCount) {
        // 创建固定大小的线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<Movie>>> futures = new ArrayList<>();
        
        try {
            // 提交所有任务
            for (int i = 1; i <= pageCount; i++) {
                final int page = i;
                Future<List<Movie>> future = executor.submit(() -> getPageData(keyword, page));
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

    //获取一页函数，传入搜索关键词和页码，返回这一页数据
    private List<Movie> getPageData(String keyword, int page) {
        //https://www.chabeigu.com/index.php/vod/search/page/{页码}/wd/{搜索关键词}.html
        //构建搜索url
        String searchUrl = "https://www.chabeigu.com" + "/index.php/vod/search/page/" + page + "/wd/" + keyword + ".html";
        String html = sendGetRequest(searchUrl);

        //创建jsoup对象
        Document doc = Jsoup.parse(html);
        //查询类为module-search-item
        Elements movieItems = doc.select(".module-search-item");
        
        //创建Movie列表
        List<Movie> movies = new ArrayList<>();

        for (Element item : movieItems) {
            System.out.println("==================================");
            // 使用更简单的相对选择器
            String name = item.select(".video-info-header h3 a").text();
            // 选择剧情描述
            String description = item.select(".video-info-items .video-info-item").last().text();
            // 获取播放链接
            String playUrl = "https://www.chabeigu.com" + item.select(".video-info-header h3 a").attr("href");
            // 获取集数信息
            String cache = item.select(".video-info-header .video-serial").text();
            
            //cache存储是否完结和多少集信息
            //匹配是否有"完结"字符串
            boolean isFinished = cache.contains("完结");
            int episodes = 0;

            //如果是XX版，则被判断为电影，则将isFinished设置为true，集数为1
            if (cache.contains("版")) {
                isFinished = true;
                episodes = 1;
            }
            // 提取集数
            else if (cache.contains("第") && cache.contains("集")) {
                // 提取"第X集"中的数字
                String episodesStr = cache.replaceAll(".*第(\\d+)集.*", "$1");
                try {
                    episodes = Integer.parseInt(episodesStr);
                } catch (NumberFormatException e) {
                    episodes = 0;
                }
            } else if (cache.startsWith("更新至")) {
                // 处理"更新至第02集"这类情况
                String episodesStr = cache.replaceAll("更新至第(\\d+)集.*", "$1");
                try {
                    episodes = Integer.parseInt(episodesStr);
                } catch (NumberFormatException e) {
                    episodes = 0;
                }
            }

            System.out.println("name: " + name);
            System.out.println("description: " + description);
            System.out.println("playUrl: " + playUrl);
            System.out.println("cache: " + cache);
            System.out.println("isFinished: " + isFinished);
            System.out.println("episodes: " + episodes);
            
            //创建Movie对象并设置属性
            Movie movie = new Movie();
            movie.setName(name);
            movie.setDescription(description);
            movie.setPlayUrl(playUrl);
            movie.setFinished(isFinished);
            movie.setEpisodes(episodes);
            
            //添加到列表
            movies.add(movie);
        }

        //返回Movie列表
        return movies;
    }

    private String sendGetRequest(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

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



        return null;
    }

    /**
     * 获取指定剧集的M3U8播放地址
     * 
     * @param baseUrl 基础URL
     * @param episodeUrl 剧集播放地址
     * @return M3U8播放地址
     */
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        return baseUrl + "/m3u8/chabeigu_video.m3u8";
    }
}