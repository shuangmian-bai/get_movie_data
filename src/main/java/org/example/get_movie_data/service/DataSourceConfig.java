package org.example.get_movie_data.service;

import org.springframework.stereotype.Component;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@Component
public class DataSourceConfig {
    
    private List<Datasource> datasources;
    private List<UrlMapping> urlMappings;

    @XmlElement(name = "datasource")
    public List<Datasource> getDatasources() {
        return datasources;
    }

    public void setDatasources(List<Datasource> datasources) {
        this.datasources = datasources;
    }

    @XmlElement(name = "urlMapping")
    public List<UrlMapping> getUrlMappings() {
        return urlMappings;
    }

    public void setUrlMappings(List<UrlMapping> urlMappings) {
        this.urlMappings = urlMappings;
    }

    @XmlRootElement(name = "datasource")
    public static class Datasource {
        private String id;
        private String clazz;
        private String name;
        private String description;

        public String getId() {
            return id;
        }

        @XmlElement(name = "id")
        public void setId(String id) {
            this.id = id;
        }

        public String getClazz() {
            return clazz;
        }

        @XmlElement(name = "class")
        public void setClazz(String clazz) {
            this.clazz = clazz;
        }

        public String getName() {
            return name;
        }

        @XmlElement(name = "name")
        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        @XmlElement(name = "description")
        public void setDescription(String description) {
            this.description = description;
        }
    }

    @XmlRootElement(name = "urlMapping")
    public static class UrlMapping {
        private String baseUrl;
        private String datasource;

        public String getBaseUrl() {
            return baseUrl;
        }

        @XmlElement(name = "baseUrl")
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDatasource() {
            return datasource;
        }

        @XmlElement(name = "datasource")
        public void setDatasource(String datasource) {
            this.datasource = datasource;
        }
    }
}