package com.example.custom;

import java.util.ArrayList;
import java.util.List;

public class CustomMovieService {
    
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        List<Movie> movies = new ArrayList<>();
        
        Movie movie = new Movie();
        movie.setName("自定义数据源电影: " + keyword);
        movie.setDescription("这是通过自定义数据源获取的电影信息");
        movie.setFinished(true);
        movie.setPlayUrl(baseUrl + "/play/custom_" + keyword);
        movie.setEpisodes(12);
        
        movies.add(movie);
        return movies;
    }

    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        List<Movie.Episode> episodes = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("自定义第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/custom_" + i);
            episodes.add(episode);
        }
        return episodes;
    }

    public String getM3u8Url(String baseUrl, String episodeUrl) {
        return baseUrl + "/m3u8/custom_video.m3u8";
    }
}