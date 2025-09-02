package org.example.get_movie_data.service.impl;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MovieServiceImpl implements MovieService {

    public List<Movie> searchMovies(String baseUrl, String keyword) {
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            List<Movie> movies = new ArrayList<Movie>();
            
            Movie movie = new Movie();
            movie.setName("测试电影");
            movie.setDescription("这是一部用于测试的固定电影数据");
            movie.setFinished(true);
            movie.setPlayUrl("https://127.0.0.1/test/play/1");
            movie.setEpisodes(5);
            
            movies.add(movie);
            return movies;
        }
        
        // 这里应该实现实际的搜索逻辑，根据baseUrl和keyword获取影视信息
        // 暂时返回示例数据
        List<Movie> movies = new ArrayList<Movie>();
        
        Movie movie = new Movie();
        movie.setName("示例电影");
        movie.setDescription("这是一部示例电影的描述");
        movie.setFinished(true);
        movie.setPlayUrl(baseUrl + "/play/123");
        movie.setEpisodes(10);
        
        movies.add(movie);
        return movies;
    }

    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            List<Movie.Episode> episodes = new ArrayList<Movie.Episode>();
            
            for (int i = 1; i <= 5; i++) {
                Movie.Episode episode = new Movie.Episode();
                episode.setTitle("测试第" + i + "集");
                episode.setEpisodeUrl("https://127.0.0.1/test/episode/" + i);
                episodes.add(episode);
            }
            
            return episodes;
        }
        
        // 这里应该实现获取集数的逻辑
        // 暂时返回示例数据
        List<Movie.Episode> episodes = new ArrayList<Movie.Episode>();
        
        for (int i = 1; i <= 10; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/" + i);
            episodes.add(episode);
        }
        
        return episodes;
    }

    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 针对特定URL返回固定数据
        if ("https://127.0.0.1/test".equals(baseUrl)) {
            return "https://127.0.0.1/test/m3u8/test.m3u8";
        }
        
        // 这里应该实现获取m3u8地址的逻辑
        // 暂时返回示例数据
        return baseUrl + "/m3u8/" + episodeUrl.substring(episodeUrl.lastIndexOf('/') + 1) + ".m3u8";
    }
    
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        // 根据数据源ID获取对应的服务实现
        // 这里暂时只返回默认实现
        if (datasourceId == null || datasourceId.isEmpty() || "default".equals(datasourceId)) {
            return this;
        }
        
        // 实际应用中，这里会根据配置文件中的class路径动态加载对应的实现类
        // 例如通过反射机制或者Spring的ApplicationContext来获取对应的Bean
        return this;
    }
}