package org.example.get_movie_data.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * 电影信息模型类
 * 
 * 用于表示电影的基本信息，包括名称、描述、是否完结、播放地址、集数和剧集列表等。
 * 该类支持XML序列化和反序列化。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@XmlRootElement(name = "movie")
@XmlType(propOrder = {"name", "description", "finished", "playUrl", "episodes", "poster", "baseUrl", "episodeList"})
public class Movie {
    
    /** 电影名称 */
    private String name;
    
    /** 电影描述 */
    private String description;
    
    /** 是否完结 */
    private boolean finished;
    
    /** 播放地址 */
    private String playUrl;
    
    /** 集数 */
    private int episodes;
    
    /** 海报图片地址 */
    private String poster;
    
    /** 数据源基础URL */
    private String baseUrl;
    
    /** 剧集列表 */
    private List<Episode> episodeList;

    /**
     * 获取电影名称
     * 
     * @return 电影名称
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    /**
     * 设置电影名称
     * 
     * @param name 电影名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取电影描述
     * 
     * @return 电影描述
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    /**
     * 设置电影描述
     * 
     * @param description 电影描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取电影是否完结状态
     * 
     * @return true表示已完结，false表示未完结
     */
    @XmlElement(name = "finished")
    public boolean isFinished() {
        return finished;
    }

    /**
     * 设置电影是否完结状态
     * 
     * @param finished true表示已完结，false表示未完结
     */
    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    /**
     * 获取播放地址
     * 
     * @return 播放地址
     */
    @XmlElement(name = "playUrl")
    public String getPlayUrl() {
        return playUrl;
    }

    /**
     * 设置播放地址
     * 
     * @param playUrl 播放地址
     */
    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    /**
     * 获取总集数
     * 
     * @return 总集数
     */
    @XmlElement(name = "episodes")
    public int getEpisodes() {
        return episodes;
    }

    /**
     * 设置总集数
     * 
     * @param episodes 总集数
     */
    public void setEpisodes(int episodes) {
        this.episodes = episodes;
    }

    /**
     * 获取海报图片地址
     *
     * @return 海报图片地址
     */
    @XmlElement(name = "poster")
    public String getPoster() {
        return poster;
    }

    /**
     * 设置海报图片地址
     *
     * @param poster 海报图片地址
     */
    public void setPoster(String poster) {
        this.poster = poster;
    }

    /**
     * 获取数据源基础URL
     *
     * @return 数据源基础URL
     */
    @XmlElement(name = "baseUrl")
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置数据源基础URL
     *
     * @param baseUrl 数据源基础URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取剧集列表
     * 
     * @return 剧集列表
     */
    @XmlElementWrapper(name = "episodeList")
    @XmlElement(name = "episode")
    public List<Episode> getEpisodeList() {
        return episodeList;
    }

    /**
     * 设置剧集列表
     * 
     * @param episodeList 剧集列表
     */
    public void setEpisodeList(List<Episode> episodeList) {
        this.episodeList = episodeList;
    }

    /**
     * 剧集信息内部类
     * 
     * 表示电影中单个剧集的信息，包括标题和播放地址。
     */
    public static class Episode {
        /** 剧集标题 */
        private String title;
        
        /** 剧集播放地址 */
        private String episodeUrl;

        /**
         * 获取剧集标题
         * 
         * @return 剧集标题
         */
        @XmlElement(name = "title")
        public String getTitle() {
            return title;
        }

        /**
         * 设置剧集标题
         * 
         * @param title 剧集标题
         */
        public void setTitle(String title) {
            this.title = title;
        }

        /**
         * 获取剧集播放地址
         * 
         * @return 剧集播放地址
         */
        @XmlElement(name = "episodeUrl")
        public String getEpisodeUrl() {
            return episodeUrl;
        }

        /**
         * 设置剧集播放地址
         * 
         * @param episodeUrl 剧集播放地址
         */
        public void setEpisodeUrl(String episodeUrl) {
            this.episodeUrl = episodeUrl;
        }
    }
}