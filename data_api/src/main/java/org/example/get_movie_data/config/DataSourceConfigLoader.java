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
            
            // 首先尝试从外部文件加载配置
            File externalConfig = new File("config/movie-data-config.xml");
            if (externalConfig.exists()) {
                System.out.println("Loading configuration from external file: " + externalConfig.getAbsolutePath());
                JAXBContext context = JAXBContext.newInstance(DataSourceConfig.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                DataSourceConfig config = (DataSourceConfig) unmarshaller.unmarshal(externalConfig);
                System.out.println("Successfully loaded external configuration");
                return config;
            }
            
            // 获取配置文件资源
            Resource resource = new ClassPathResource("config/movie-data-config.xml");
            System.out.println("Resource exists: " + resource.exists());
            System.out.println("Resource description: " + resource.getDescription());
            
            // 检查输入流是否可用
            InputStream inputStream = resource.getInputStream();
            System.out.println("Input stream available: " + (inputStream != null && inputStream.available() > 0));
            
            // 创建JAXB上下文和解组器
            JAXBContext context = JAXBContext.newInstance(DataSourceConfig.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            
            // 解析XML配置文件
            DataSourceConfig config = (DataSourceConfig) unmarshaller.unmarshal(inputStream);
            
            // 输出加载结果用于调试
            System.out.println("Successfully loaded configuration:");
            System.out.println("Config object: " + config);
            
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
}