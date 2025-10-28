package org.example.get_movie_data.config;

import org.example.get_movie_data.service.DataSourceConfig;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * 配置文件清理工具
 * 
 * 用于清理配置文件中的重复项
 */
public class CleanConfigFile {
    
    public static void main(String[] args) {
        try {
            // 读取配置文件
            File configFile = new File("config/movie-data-config.xml");
            if (!configFile.exists()) {
                System.out.println("配置文件不存在: " + configFile.getAbsolutePath());
                return;
            }
            
            // 解析XML配置文件
            JAXBContext context = JAXBContext.newInstance(DataSourceConfig.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            DataSourceConfig config = (DataSourceConfig) unmarshaller.unmarshal(configFile);
            
            System.out.println("原始配置:");
            System.out.println("数据源数量: " + config.getDatasources().size());
            System.out.println("URL映射数量: " + config.getUrlMappings().size());
            
            // 去重数据源（基于ID）
            Map<String, DataSourceConfig.Datasource> uniqueDatasources = new LinkedHashMap<>();
            for (DataSourceConfig.Datasource datasource : config.getDatasources()) {
                if (!uniqueDatasources.containsKey(datasource.getId())) {
                    uniqueDatasources.put(datasource.getId(), datasource);
                }
            }
            
            // 去重URL映射（基于baseUrl）
            Map<String, DataSourceConfig.UrlMapping> uniqueUrlMappings = new LinkedHashMap<>();
            for (DataSourceConfig.UrlMapping urlMapping : config.getUrlMappings()) {
                if (!uniqueUrlMappings.containsKey(urlMapping.getBaseUrl())) {
                    uniqueUrlMappings.put(urlMapping.getBaseUrl(), urlMapping);
                }
            }
            
            // 更新配置
            config.setDatasources(new ArrayList<>(uniqueDatasources.values()));
            config.setUrlMappings(new ArrayList<>(uniqueUrlMappings.values()));
            
            System.out.println("\n清理后配置:");
            System.out.println("数据源数量: " + config.getDatasources().size());
            System.out.println("URL映射数量: " + config.getUrlMappings().size());
            
            // 保存回文件
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(config, configFile);
            
            System.out.println("\n配置文件已更新: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("清理配置文件时出错:");
            e.printStackTrace();
        }
    }
}