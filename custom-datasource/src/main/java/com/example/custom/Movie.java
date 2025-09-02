package com.example.custom;

import java.util.List;

/**
 * 电影信息模型类（外部数据源版本）
 * 
 * 这是外部数据源使用的电影信息模型类，与主项目中的模型类结构相似但独立。
 * 适配器会自动将此类转换为主项目中的Movie类。
 * 
 * @author custom-datasource team
 * @version 1.0.0
 */
public class Movie {
    /** 电影名称 */
    private String name;
    
    /** 电影描述 */
    private String description;
    
    /** 是否完结 */
    private boolean finished;
    
    /** 播放地址 */
    private String playUrl;
    
    /** 总集数 */
    private int episodes;
    
    /** 剧集列表 */
    private List<Episode> episodeList;

    /**
     * 获取电影名称
     * 
     * @return 电影名称
     */
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
     * 获取剧集列表
     * 
     * @return 剧集列表
     */
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