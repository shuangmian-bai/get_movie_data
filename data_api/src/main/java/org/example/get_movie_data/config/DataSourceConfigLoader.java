package org.example.get_movie_data.config;

import org.example.get_movie_data.service.DataSourceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源配置加载器
 * 
 * 负责从classpath中加载和解析XML配置文件，创建DataSourceConfig实例。
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Configuration
public class DataSourceConfigLoader {

    /**
     * 加载数据源配置
     * 
     * 从classpath的config/movie-data-config.xml路径加载XML配置文件，
     * 并将其解析为DataSourceConfig对象。
     * 
     * @return DataSourceConfig实例
     */
    @Bean
    public DataSourceConfig dataSourceConfig() {
        try {
            System.out.println("Attempting to load configuration file...");
            
            // 从外部文件加载配置 (相对于应用运行目录)
            File externalConfig = Paths.get("config", "movie-data-config.xml").toFile();
            System.out.println("Loading configuration from external file: " + externalConfig.getAbsolutePath());
            
            DataSourceConfig config;
            if (externalConfig.exists()) {
                JAXBContext context = JAXBContext.newInstance(DataSourceConfig.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                config = (DataSourceConfig) unmarshaller.unmarshal(externalConfig);
                System.out.println("Successfully loaded external configuration");
            } else {
                System.out.println("External configuration file not found at: " + externalConfig.getAbsolutePath());
                
                // 获取配置文件资源 (从JAR内部)
                Resource resource = new ClassPathResource("config/movie-data-config.xml");
                System.out.println("Internal resource exists: " + resource.exists());
                System.out.println("Internal resource description: " + resource.getDescription());
                
                // 检查输入流是否可用
                InputStream inputStream = resource.getInputStream();
                System.out.println("Input stream available: " + (inputStream != null && inputStream.available() > 0));
                
                // 创建JAXB上下文和解组器
                JAXBContext context = JAXBContext.newInstance(DataSourceConfig.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                
                // 解析XML配置文件
                config = (DataSourceConfig) unmarshaller.unmarshal(inputStream);
                
                System.out.println("Successfully loaded internal configuration:");
                System.out.println("Config object: " + config);
            }
            
            // 对配置进行去重处理
            removeDuplicates(config);
            
            // 输出加载结果用于调试
            System.out.println("Configuration loaded:");
            if (config.getDatasources() != null) {
                System.out.println("Datasources count: " + config.getDatasources().size());
                for (DataSourceConfig.Datasource datasource : config.getDatasources()) {
                    System.out.println("  - " + datasource.getId() + " -> " + datasource.getClazz());
                }
            }
            
            if (config.getUrlMappings() != null) {
                System.out.println("URL mappings count: " + config.getUrlMappings().size());
                for (DataSourceConfig.UrlMapping mapping : config.getUrlMappings()) {
                    System.out.println("  - " + mapping.getBaseUrl() + " -> " + mapping.getDatasource());
                }
            }
            
            return config;
        } catch (Exception e) {
            System.err.println("Failed to load configuration file:");
            e.printStackTrace();
            return new DataSourceConfig(); // 返回空配置而不是null
        }
    }
    
    /**
     * 移除配置中的重复项
     * 
     * @param config 配置对象
     */
    private void removeDuplicates(DataSourceConfig config) {
        if (config.getDatasources() != null) {
            // 基于ID去重数据源
            Map<String, DataSourceConfig.Datasource> uniqueDatasources = new LinkedHashMap<>();
            for (DataSourceConfig.Datasource datasource : config.getDatasources()) {
                // 如果ID已经存在，保留第一个出现的
                if (!uniqueDatasources.containsKey(datasource.getId())) {
                    uniqueDatasources.put(datasource.getId(), datasource);
                }
            }
            config.setDatasources(new ArrayList<>(uniqueDatasources.values()));
        }
        
        if (config.getUrlMappings() != null) {
            // 基于baseUrl去重URL映射
            Map<String, DataSourceConfig.UrlMapping> uniqueUrlMappings = new LinkedHashMap<>();
            for (DataSourceConfig.UrlMapping urlMapping : config.getUrlMappings()) {
                // 如果baseUrl已经存在，保留第一个出现的
                if (!uniqueUrlMappings.containsKey(urlMapping.getBaseUrl())) {
                    uniqueUrlMappings.put(urlMapping.getBaseUrl(), urlMapping);
                }
            }
            config.setUrlMappings(new ArrayList<>(uniqueUrlMappings.values()));
        }
    }
}