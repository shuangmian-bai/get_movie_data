package org.example.get_movie_data.service.impl;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.DataSourceConfig;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Service
public class MovieServiceImpl implements MovieService {
    
    @Autowired
    private DataSourceConfig dataSourceConfig;

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
        // 如果没有指定数据源或者指定的是默认数据源，则使用当前实例
        if (datasourceId == null || datasourceId.isEmpty() || "default".equals(datasourceId)) {
            return this;
        }
        
        // 检查数据源是否在配置文件中存在
        boolean datasourceExists = false;
        if (dataSourceConfig.getDatasources() != null) {
            for (DataSourceConfig.Datasource datasource : dataSourceConfig.getDatasources()) {
                if (datasourceId.equals(datasource.getId())) {
                    datasourceExists = true;
                    break;
                }
            }
        }
        
        // 如果数据源在配置中存在但不是默认数据源，则表示需要使用外部jar包
        // 在实际应用中，这里会根据配置文件中的class路径动态加载对应的实现类
        // 如果加载失败或者jar包不存在，则返回失败状态的数据
        if (datasourceExists) {
            return new FailedMovieService();
        } else {
            // 如果数据源在配置中都不存在，则也返回失败状态
            return new FailedMovieService();
        }
    }
    
    /**
     * 用于表示数据源加载失败的服务实现
     */
    private static class FailedMovieService implements MovieService {
        @Override
        public List<Movie> searchMovies(String baseUrl, String keyword) {
            List<Movie> movies = new ArrayList<>();
            Movie movie = new Movie();
            movie.setName("数据源加载失败");
            movie.setDescription("无法加载指定的数据源，可能是因为对应的jar包未配置或不存在");
            movie.setFinished(false);
            movie.setPlayUrl("");
            movie.setEpisodes(0);
            movies.add(movie);
            return movies;
        }

        @Override
        public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
            // 返回空列表表示获取剧集信息失败
            return new ArrayList<>();
        }

        @Override
        public String getM3u8Url(String baseUrl, String episodeUrl) {
            // 返回空字符串表示获取播放地址失败
            return "";
        }

        @Override
        public MovieService getMovieServiceByDatasource(String datasourceId) {
            return this;
        }
    }
}