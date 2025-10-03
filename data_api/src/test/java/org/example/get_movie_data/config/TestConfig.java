package org.example.get_movie_data.config;

import org.example.get_movie_data.service.MovieService;
import org.example.get_movie_data.service.MovieServiceManager;
import org.example.get_movie_data.service.ConfigManager;
import org.example.get_movie_data.service.DataSourceConfig;
import org.example.get_movie_data.service.CacheManager;
import org.example.get_movie_data.service.CachedMovieService;
import org.example.get_movie_data.service.ExternalServiceFactory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试配置类
 * 
 * 用于在测试环境中提供模拟的Bean，避免真实的数据源调用
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    /**
     * 提供测试用的ConfigManager Bean
     * 
     * @return 模拟的ConfigManager实例
     */
    @Bean
    @Primary
    public DataSourceConfig testDataSourceConfig() {
        // 创建测试用的配置
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        
        // 创建URL映射配置
        List<DataSourceConfig.UrlMapping> urlMappings = new ArrayList<>();
        DataSourceConfig.UrlMapping testMapping = new DataSourceConfig.UrlMapping();
        testMapping.setBaseUrl("https://127.0.0.1/test");
        testMapping.setDatasource("test");
        urlMappings.add(testMapping);
        
        // 添加通配符映射
        DataSourceConfig.UrlMapping wildcardMapping = new DataSourceConfig.UrlMapping();
        wildcardMapping.setBaseUrl("*");
        wildcardMapping.setDatasource("default");
        urlMappings.add(wildcardMapping);
        
        dataSourceConfig.setUrlMappings(urlMappings);
        
        // 创建数据源配置
        List<DataSourceConfig.Datasource> datasources = new ArrayList<>();
        DataSourceConfig.Datasource testDatasource = new DataSourceConfig.Datasource();
        testDatasource.setId("test");
        testDatasource.setClazz("org.example.get_movie_data.service.MovieServiceManager$DefaultMovieService");
        datasources.add(testDatasource);
        
        dataSourceConfig.setDatasources(datasources);
        
        return dataSourceConfig;
    }

    @Bean
    @Primary
    public ConfigManager testConfigManager() {
        ConfigManager configManager = new ConfigManager();
        
        // 使用反射设置私有字段
        try {
            java.lang.reflect.Field dataSourceConfigField = ConfigManager.class.getDeclaredField("dataSourceConfig");
            dataSourceConfigField.setAccessible(true);
            dataSourceConfigField.set(configManager, testDataSourceConfig());
            
            // 调用初始化方法
            java.lang.reflect.Method initMethod = ConfigManager.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(configManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return configManager;
    }

    /**
     * 提供测试用的MovieServiceManager Bean
     * 
     * @return 配置了测试服务的MovieServiceManager实例
     */
    @Bean
    @Primary
    public MovieServiceManager testMovieServiceManager() {
        MovieServiceManager serviceManager = new MovieServiceManager();
        
        // 使用反射设置私有字段
        try {
            java.lang.reflect.Field configManagerField = MovieServiceManager.class.getDeclaredField("configManager");
            configManagerField.setAccessible(true);
            configManagerField.set(serviceManager, testConfigManager());
            
            // 初始化缓存管理器和外部服务工厂
            java.lang.reflect.Field cacheManagerField = MovieServiceManager.class.getDeclaredField("cacheManager");
            cacheManagerField.setAccessible(true);
            cacheManagerField.set(serviceManager, new CacheManager());
            
            java.lang.reflect.Field externalServiceFactoryField = MovieServiceManager.class.getDeclaredField("externalServiceFactory");
            externalServiceFactoryField.setAccessible(true);
            externalServiceFactoryField.set(serviceManager, new ExternalServiceFactory(new CacheManager()));
            
            // 调用初始化方法
            java.lang.reflect.Method initMethod = MovieServiceManager.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(serviceManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return serviceManager;
    }
}