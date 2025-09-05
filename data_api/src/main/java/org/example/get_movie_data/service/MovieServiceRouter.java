package org.example.get_movie_data.service;

import org.example.get_movie_data.model.Movie;
import java.util.List;

/**
 * 电影服务路由器接口
 * 
 * 定义了根据URL获取电影服务的接口，用于解耦Controller和具体的数据源管理实现。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
public interface MovieServiceRouter {

    /**
     * 根据基础URL获取对应的电影服务实例
     * 
     * @param baseUrl 基础URL
     * @return 对应的电影服务实例
     */
    MovieService getMovieServiceByBaseUrl(String baseUrl);
}