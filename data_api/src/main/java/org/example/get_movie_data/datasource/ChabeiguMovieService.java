package org.example.get_movie_data.datasource;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.util.HttpClientUtil;
import org.example.get_movie_data.util.HtmlParserUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import org.example.get_movie_data.model.Movie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 茶杯狐电影服务实现类
 * 
 * 这是一个外部数据源的示例实现，展示了如何创建一个可被主项目加载的外部数据源。
 * 该类不需要实现主项目中的任何接口，适配器会通过反射调用其方法。
 * 
 * @author chabeigu team
 * @version 1.0.0
 */
@DataSource(
    id = "chabeigu",
    name = "茶杯狐数据源",
    description = "从茶杯狐网站获取电影数据",
    baseUrl = "https://www.chabeigu.com",
    version = "1.0.0"
)
public class ChabeiguMovieService implements MovieService {
    
    // 线程池大小控制变量
    private static final int THREAD_POOL_SIZE = 10;
    
    /**
     * 根据关键词搜索电影
     * 
     * @param baseUrl 基础URL
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        //搜索接口https://www.chabeigu.com/index.php/vod/search.html?wd={keyword}
        //构建搜索url
        String searchUrl = baseUrl + "/index.php/vod/search.html?wd=" + keyword;

        //发送get请求
        String html = HttpClientUtil.sendGetRequest(searchUrl);
        //创建jsoup对象
        Document doc = HtmlParserUtil.parse(html);

        //获取到有多少页数据
        Elements pageElements = HtmlParserUtil.select(doc, "#page > a:nth-child(9)");
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
        // 使用多线程获取所有页面数据
        List<Movie> movies = getAllPageData(baseUrl, keyword, pageCount);

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

    //获取一页函数，传入搜索关键词和页码，返回这一页数据
    private List<Movie> getPageData(String baseUrl, String keyword, int page) {
        //https://www.chabeigu.com/index.php/vod/search/page/{页码}/wd/{搜索关键词}.html
        //构建搜索url
        String searchUrl = baseUrl + "/index.php/vod/search/page/" + page + "/wd/" + keyword + ".html";
        String html = HttpClientUtil.sendGetRequest(searchUrl);

        //创建jsoup对象
        Document doc = HtmlParserUtil.parse(html);
        //查询类为module-search-item
        Elements movieItems = HtmlParserUtil.select(doc, ".module-search-item");
        
        //创建Movie列表
        List<Movie> movies = new ArrayList<>();

        for (Element item : movieItems) {
            try {
                // 使用更简单的相对选择器
                Elements nameElements = HtmlParserUtil.select(item, ".video-info-header h3 a");
                String name = !nameElements.isEmpty() ? nameElements.first().text() : "未知电影名称";
                
                // 选择剧情描述
                Elements descElements = HtmlParserUtil.select(item, ".video-info-items .video-info-item");
                String description = !descElements.isEmpty() ? descElements.last().text() : "暂无描述";
                
                // 获取播放链接
                String playUrl = !nameElements.isEmpty() ? 
                    baseUrl + nameElements.first().attr("href") : baseUrl;
                
                //获取海报信息
                Elements posterElements = HtmlParserUtil.select(item, ".lazy.lazyload");
                String poster = !posterElements.isEmpty() ? 
                    posterElements.first().attr("data-src") : "";
                
                //获取类型信息
                String type = descElements.size() > 0 ? descElements.first().text() : "未知类型";
                
                //获取地区信息
                String area = descElements.size() > 1 ? descElements.get(1).text() : "未知地区";

                //创建Movie对象并设置属性
                Movie movie = new Movie();
                movie.setName(name);
                movie.setDescription(description);
                movie.setPlayUrl(playUrl);
                movie.setPoster(poster);
                
                //添加到列表
                movies.add(movie);
            } catch (Exception e) {
                System.err.println("解析电影信息时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        //返回Movie列表
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
        String html = HttpClientUtil.sendGetRequest(playUrl);
        if (html == null || html.isEmpty()) {
            return new ArrayList<>();
        }
        
        //创建jsoup对象
        Document doc = HtmlParserUtil.parse(html);
        Elements elements = HtmlParserUtil.select(doc, "#sort-item-1 a");
        // 创建剧集列表
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (Element element : elements) {
            try {
                Elements spanElements = HtmlParserUtil.select(element, "span");
                String title = !spanElements.isEmpty() ? spanElements.first().text() : "未知剧集";
                String episodeUrl = element.attr("href");
                
                // 检查是否为相对链接
                if (!episodeUrl.startsWith("http")) {
                    episodeUrl = baseUrl + episodeUrl;
                }

                // 构造剧集对象并添加到列表中
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle(title);
                episode.setEpisodeUrl(episodeUrl);
                episodes.add(episode);
            } catch (Exception e) {
                System.err.println("解析剧集信息时出错: " + e.getMessage());
                e.printStackTrace();
            }
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
    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        String html = HttpClientUtil.sendGetRequest(episodeUrl);
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        //使用正则表达式匹配 最小 http**m3u8
        Pattern pattern = Pattern.compile("https?://[^\"]*m3u8");
        Matcher matcher = pattern.matcher(html);
        String m3u8Url = "";
        if (matcher.find()) {
            m3u8Url = matcher.group();
        }

        return m3u8Url;
    }

    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        if ("chabeigu".equals(datasourceId)) {
            return this;
        }
        return null;
    }
}