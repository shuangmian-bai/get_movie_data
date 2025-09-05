package com.example.chabeigu;

import java.util.List;

/**
 * 电影数据封装类（茶杯狐API适配版本）
 * 
 * 此类用于封装从茶杯狐数据源获取的电影相关信息，
 * 它与主项目中使用的Movie类结构相似但独立存在。
 * 数据转换适配器会将此类实例转换为主项目中使用的Movie实例。
 * 
 * @author chabeigu development team
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
    
    /** 海报图片地址 */
    private String poster;
    
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
     * 获取海报图片地址
     *
     * @return 海报图片地址
     */
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