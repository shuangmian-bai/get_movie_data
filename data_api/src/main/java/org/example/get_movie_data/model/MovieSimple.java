package org.example.get_movie_data.model;

import javax.xml.bind.annotation.*;

/**
 * 简化的电影信息模型类
 * 
 * 用于表示电影的基础信息，不包含剧集相关字段。
 * 该类支持XML序列化和反序列化。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@XmlRootElement(name = "movie")
@XmlType(propOrder = {"name", "description", "playUrl", "poster", "type", "region"})
public class MovieSimple {
    
    /** 电影名称 */
    private String name;
    
    /** 电影描述 */
    private String description;
    
    /** 播放地址 */
    private String playUrl;
    
    /** 海报图片地址 */
    private String poster;
    
    /** 电影类型 */
    private String type;
    
    /** 地区 */
    private String region;

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
     * 获取电影类型
     *
     * @return 电影类型
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    /**
     * 设置电影类型
     *
     * @param type 电影类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取地区
     *
     * @return 地区
     */
    @XmlElement(name = "region")
    public String getRegion() {
        return region;
    }

    /**
     * 设置地区
     *
     * @param region 地区
     */
    public void setRegion(String region) {
        this.region = region;
    }
}