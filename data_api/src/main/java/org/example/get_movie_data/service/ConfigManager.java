package org.example.get_movie_data.service;

import org.example.get_movie_data.config.DataSourceConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * 配置管理服务
 * 
 * 负责管理应用程序的配置信息，包括数据源配置和URL映射配置。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Service
public class ConfigManager {

    @Autowired
    private DataSourceConfig dataSourceConfig;

    private DataSourceConfig config;

    @PostConstruct
    public void init() {
        this.config = dataSourceConfig;
    }

    /**
     * 获取数据源配置
     * 
     * @return 数据源配置
     */
    public DataSourceConfig getConfig() {
        return config;
    }
}