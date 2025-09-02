package org.example.get_movie_data.service;

import org.springframework.stereotype.Component;

import javax.xml.bind.annotation.*;
import java.util.List;

@Component
@XmlRootElement(name = "configuration")
@XmlType(propOrder = { "datasources", "urlMappings" })
public class DataSourceConfig {

    // 默认构造函数，用于JAXB反序列化
    public DataSourceConfig() {}

    private List<Datasource> datasources;
    private List<UrlMapping> urlMappings;

    @XmlElementWrapper(name = "datasources")
    @XmlElement(name = "datasource")
    public List<Datasource> getDatasources() {
        return datasources;
    }

    public void setDatasources(List<Datasource> datasources) {
        this.datasources = datasources;
    }

    @XmlElementWrapper(name = "urlMappings")
    @XmlElement(name = "urlMapping")
    public List<UrlMapping> getUrlMappings() {
        return urlMappings;
    }

    public void setUrlMappings(List<UrlMapping> urlMappings) {
        this.urlMappings = urlMappings;
    }

    public static class Datasource {
        private String id;
        private String clazz;
        private String name;
        private String description;

        public Datasource() {}

        public String getId() {
            return id;
        }

        @XmlAttribute
        public void setId(String id) {
            this.id = id;
        }

        public String getClazz() {
            return clazz;
        }

        @XmlAttribute(name = "class")
        public void setClazz(String clazz) {
            this.clazz = clazz;
        }

        public String getName() {
            return name;
        }

        @XmlElement
        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        @XmlElement
        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "Datasource{" +
                    "id='" + id + '\'' +
                    ", clazz='" + clazz + '\'' +
                    ", name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    public static class UrlMapping {
        private String baseUrl;
        private String datasource;

        public UrlMapping() {}

        public String getBaseUrl() {
            return baseUrl;
        }

        @XmlAttribute
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDatasource() {
            return datasource;
        }

        @XmlAttribute
        public void setDatasource(String datasource) {
            this.datasource = datasource;
        }

        @Override
        public String toString() {
            return "UrlMapping{" +
                    "baseUrl='" + baseUrl + '\'' +
                    ", datasource='" + datasource + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DataSourceConfig{" +
                "datasources=" + datasources +
                ", urlMappings=" + urlMappings +
                '}';
    }
}