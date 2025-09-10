package com.example.yuny;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 云云TV电影服务实现类
 * 
 * 实现了MovieService接口，提供对云云TV网站数据的抓取和处理功能
 * 
 * @author yuny development team
 * @version 1.0.0
 */
public class YunyMovieService implements MovieService {
    
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


            getMovies(encodedKeyword, 1, baseUrl);

            
            return new ArrayList<>(); // 暂时返回空列表
        }
        catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 返回空列表而不是null
        }
    }

    //创建爬取一页数据函数，传入搜索关键词，页码和baseurl
    public List<Movie> getMovies(String encodedKeyword, int page, String baseUrl) {
        //https://www.yuny.tv/videoSearch?key={keyword}&current={page}
        //构造url，注意这里keyword已经是编码过的了
        String url = baseUrl + "/videoSearch?key=" + encodedKeyword + "&current=" + page;
        System.out.println("正在请求URL: " + url);

        //发送请求
        String html = HttpUtils.get(url);

        //创建doc解析对象
        Document doc = Jsoup.parse(html);

        //获取#__nuxt > div > section > section > div.flex-1.flex.flex-col.overflow-y-auto > div.flex-1.pr-5 > div:nth-child(2) > div.search_result_list.flex.flex-wrap.gap-\[--fs-spacing\]的全部a标签并且遍历
        for (Element a : doc.select("div.search_result_list").select("a")) {
            System.out.println("======================================");
            System.out.println(a);
        }

        return new ArrayList<>();

    }


    /**
     * 根据播放地址获取影视的全部集数和标题以及播放地址
     * @param baseUrl 基础URL
     * @param playUrl 播放地址
     * @return 影视剧集列表
     */
    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        // TODO: 实现具体的剧集获取逻辑
        // 这里暂时返回空列表，实际应该通过网络请求获取数据
        return new ArrayList<>();
    }

    /**
     * 获取具体播放地址的m3u8
     * @param baseUrl 基础URL
     * @param episodeUrl 具体播放地址
     * @return m3u8地址
     */
    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // TODO: 实现具体的m3u8地址解析逻辑
        // 这里暂时返回空字符串，实际应该通过网络请求获取数据
        return "";
    }
}