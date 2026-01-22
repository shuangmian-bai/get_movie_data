package org.example.get_movie_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 配置管理器
 * 
 * 由于现在使用注解方式管理数据源，此管理器简化为基本实现
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
public class ConfigManager {
    
    @Autowired
    private DataSourceConfig dataSourceConfig;
    
    /**
     * 获取数据源配置
     * 
     * @return 数据源配置对象
     */
    public DataSourceConfig getConfig() {
        return dataSourceConfig;
    }
}