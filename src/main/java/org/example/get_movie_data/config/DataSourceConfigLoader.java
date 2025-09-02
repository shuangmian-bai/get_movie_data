package org.example.get_movie_data.config;

import org.example.get_movie_data.service.DataSourceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;

@Configuration
public class DataSourceConfigLoader {

    @Bean
    public DataSourceConfig dataSourceConfig() {
        try {
            System.out.println("Attempting to load configuration file...");
            
            JAXBContext jaxbContext = JAXBContext.newInstance(DataSourceConfig.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            
            ClassPathResource resource = new ClassPathResource("config/movie-data-config.xml");
            System.out.println("Resource exists: " + resource.exists());
            System.out.println("Resource path: " + resource.getPath());
            
            InputStream inputStream = resource.getInputStream();
            System.out.println("Input stream available: " + (inputStream != null));
            
            DataSourceConfig config = (DataSourceConfig) unmarshaller.unmarshal(inputStream);
            
            System.out.println("Successfully loaded configuration:");
            System.out.println("Config object: " + config);
            
            System.out.println("Successfully loaded configuration:");
            if (config.getDatasources() != null) {
                System.out.println("Datasources count: " + config.getDatasources().size());
                config.getDatasources().forEach(ds -> 
                    System.out.println("  - " + ds.getId() + " -> " + ds.getClazz()));
            } else {
                System.out.println("Datasources is null");
            }
            
            if (config.getUrlMappings() != null) {
                System.out.println("URL mappings count: " + config.getUrlMappings().size());
                config.getUrlMappings().forEach(mapping -> 
                    System.out.println("  - " + mapping.getBaseUrl() + " -> " + mapping.getDatasource()));
            } else {
                System.out.println("URL mappings is null");
            }
            
            return config;
        } catch (Exception e) {
            System.err.println("Failed to load configuration file:");
            e.printStackTrace();
            
            // Return empty config as fallback
            return new DataSourceConfig();
        }
    }
}