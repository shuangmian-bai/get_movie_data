package org.example.get_movie_data.controller;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.model.MovieSimple;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.MovieServiceRouter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 电影控制器类
 * 
 * 提供电影相关的RESTful API接口。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/movie")
public class MovieController {

    @Autowired
    private MovieServiceRouter movieServiceRouter;

    /**
     * 根据搜索关键词获取影视信息（完整信息，包含剧集）
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
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        return service.searchMovies(baseUrl, keyword);
    }

    /**
     * 根据搜索关键词获取影视信息（基础信息，不包含剧集）
     * 
     * @param baseUrl 基础URL，用于确定使用哪个数据源
     * @param keyword 搜索关键词
     * @param datasource 数据源ID（可选），直接指定数据源
     * @return 影视信息列表（基础信息）
     */
    @GetMapping("/search/simple")
    public List<MovieSimple> searchMoviesSimple(@RequestParam String baseUrl,
                                               @RequestParam String keyword,
                                               @RequestParam(required = false) String datasource) {
        System.out.println("MovieController.searchMoviesSimple called with baseUrl: " + baseUrl + ", keyword: " + keyword + ", datasource: " + datasource);
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        List<Movie> movies = service.searchMovies(baseUrl, keyword);
        
        // 转换为MovieSimple对象，去除剧集信息
        return movies.stream().map(movie -> {
            MovieSimple simple = new MovieSimple();
            simple.setName(movie.getName());
            simple.setDescription(movie.getDescription());
            simple.setPlayUrl(movie.getPlayUrl());
            simple.setPoster(movie.getPoster());
            // 注意：MovieSimple中没有finished字段，但添加了type和region字段
            // 这里可以根据需要设置type和region的默认值或从movie中提取
            simple.setType(extractTypeFromMovie(movie));
            simple.setRegion(extractRegionFromMovie(movie));
            return simple;
        }).collect(Collectors.toList());
    }

    /**
     * 从Movie对象中提取类型信息（示例实现）
     * 
     * @param movie 电影对象
     * @return 类型信息
     */
    private String extractTypeFromMovie(Movie movie) {
        // 这里可以根据实际需求从Movie对象中提取类型信息
        // 当前为示例实现，返回默认值
        return "未知类型";
    }

    /**
     * 从Movie对象中提取地区信息（示例实现）
     * 
     * @param movie 电影对象
     * @return 地区信息
     */
    private String extractRegionFromMovie(Movie movie) {
        // 这里可以根据实际需求从Movie对象中提取地区信息
        // 当前为示例实现，返回默认值
        return "未知地区";
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
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
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
        MovieService service = movieServiceRouter.getMovieServiceByBaseUrl(baseUrl);
        return service.getM3u8Url(baseUrl, episodeUrl);
    }
}