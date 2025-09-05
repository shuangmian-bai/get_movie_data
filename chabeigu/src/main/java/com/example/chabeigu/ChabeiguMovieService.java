package com.example.chabeigu;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        getPageData(keyword, 1);

        List<Movie> movies = new ArrayList<>();




        Movie movie = new Movie();
        movie.setName("茶杯狐电影: " + keyword);
        movie.setDescription("这是通过茶杯狐数据源获取的电影信息");
        movie.setFinished(true);
        movie.setPlayUrl(baseUrl + "/play/chabeigu_" + keyword);
        movie.setEpisodes(12);

        movies.add(movie);
        return movies;
    }

    //获取一页函数，传入搜索关键词和页码，返回这一页数据
    private List<Movie> getPageData(String keyword, int page) {
        //https://www.chabeigu.com/index.php/vod/search/page/{页码}/wd/{搜索关键词}.html
        //构建搜索url
        String searchUrl = "https://www.chabeigu.com" + "/index.php/vod/search/page/" + page + "/wd/" + keyword + ".html";
        String html = sendGetRequest(searchUrl);
        System.out.println("html : "+html);


        return null;
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
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("茶杯狐第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/chabeigu_" + i);
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
        return baseUrl + "/m3u8/chabeigu_video.m3u8";
    }
}