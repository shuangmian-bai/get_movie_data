package org.example.get_movie_data.config;
import org.example.get_movie_data.service.DataSourceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化的数据源配置加载器
 * 
 * 由于不再使用XML配置，直接返回空配置
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class DataSourceConfigLoader {

/**
     * 加载数据源配置
     * 
     * 返回空配置，因为现在使用注解方式管理数据源
     * 
     * @return DataSourceConfig实例
     */
    @Bean
    public DataSourceConfig dataSourceConfig() {
        System.out.println("Using annotation-based configuration, no XML loading needed");
        return new DataSourceConfig(); // 返回空配置
   }
}