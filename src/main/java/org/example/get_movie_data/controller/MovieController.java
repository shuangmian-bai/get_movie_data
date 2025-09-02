package org.example.get_movie_data.controller;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.DataSourceManager;
import org.example.get_movie_data.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 电影数据控制器
 * 
 * 提供RESTful API接口用于获取电影相关信息，包括搜索电影、获取剧集和获取M3U8播放地址。
 * 支持通过URL参数动态选择不同的数据源。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/movie")
public class MovieController {

    @Autowired
    private DataSourceManager dataSourceManager;

    /**
     * 根据搜索关键词获取影视信息
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param keyword 搜索关键词
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视信息列表
     */
    @GetMapping("/search")
    public List<Movie> searchMovies(@RequestParam String baseUrl, 
                                   @RequestParam String keyword,
                                   @RequestParam(required = false) String datasource) {
        System.out.println("MovieController.searchMovies called with baseUrl: " + baseUrl + ", keyword: " + keyword + ", datasource: " + datasource);
        MovieService service = dataSourceManager.getMovieServiceByBaseUrl(baseUrl);
        return service.searchMovies(baseUrl, keyword);
    }

    /**
     * 根据播放地址获取影视的全部集数和标题以及播放地址
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param playUrl 播放地址
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视剧集列表
     */
    @GetMapping("/episodes")
    public List<Movie.Episode> getEpisodes(@RequestParam String baseUrl, 
                                          @RequestParam String playUrl,
                                          @RequestParam(required = false) String datasource) {
        System.out.println("MovieController.getEpisodes called with baseUrl: " + baseUrl + ", playUrl: " + playUrl + ", datasource: " + datasource);
        MovieService service = dataSourceManager.getMovieServiceByBaseUrl(baseUrl);
        return service.getEpisodes(baseUrl, playUrl);
    }

    /**
     * 获取具体播放地址的m3u8
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param episodeUrl 具体播放地址
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return m3u8地址
     */
    @GetMapping("/m3u8")
    public String getM3u8Url(@RequestParam String baseUrl, 
                            @RequestParam String episodeUrl,
                            @RequestParam(required = false) String datasource) {
        System.out.println("MovieController.getM3u8Url called with baseUrl: " + baseUrl + ", episodeUrl: " + episodeUrl + ", datasource: " + datasource);
        MovieService service = dataSourceManager.getMovieServiceByBaseUrl(baseUrl);
        return service.getM3u8Url(baseUrl, episodeUrl);
    }
}