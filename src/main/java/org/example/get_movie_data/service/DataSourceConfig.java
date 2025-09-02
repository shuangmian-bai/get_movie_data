package org.example.get_movie_data.service;

import org.springframework.stereotype.Component;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * 数据源配置类
 *
 * 用于解析和存储XML配置文件中的数据源和URL映射配置信息。
 *
 * @author get_movie_data team
 * @version 1.0.0
 */
@Component
@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataSourceConfig {

    // 默认构造函数，用于JAXB反序列化
    public DataSourceConfig() {}

    /** 数据源列表 */
    @XmlElementWrapper(name = "datasources")
    @XmlElement(name = "datasource")
    private List<Datasource> datasources;

    /** URL映射列表 */
    @XmlElementWrapper(name = "urlMappings")
    @XmlElement(name = "urlMapping")
    private List<UrlMapping> urlMappings;

    /**
     * 获取数据源列表
     *
     * @return 数据源列表
     */
    public List<Datasource> getDatasources() {
        return datasources;
    }

    /**
     * 设置数据源列表
     *
     * @param datasources 数据源列表
     */
    public void setDatasources(List<Datasource> datasources) {
        this.datasources = datasources;
    }

    /**
     * 获取URL映射列表
     *
     * @return URL映射列表
     */
    public List<UrlMapping> getUrlMappings() {
        return urlMappings;
    }

    /**
     * 设置URL映射列表
     *
     * @param urlMappings URL映射列表
     */
    public void setUrlMappings(List<UrlMapping> urlMappings) {
        this.urlMappings = urlMappings;
    }

    /**
     * 数据源配置内部类
     *
     * 表示单个数据源的配置信息，包括ID、类名、名称和描述。
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Datasource {
        /** 数据源ID */
        @XmlAttribute
        private String id;

        /** 数据源实现类的完整类名 */
        @XmlAttribute(name = "class")
        private String clazz;

        /** 数据源名称 */
        @XmlElement
        private String name;

        /** 数据源描述 */
        @XmlElement
        private String description;

        /**
         * 获取数据源ID
         *
         * @return 数据源ID
         */
        public String getId() {
            return id;
        }

        /**
         * 设置数据源ID
         *
         * @param id 数据源ID
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 获取数据源实现类的完整类名
         *
         * @return 类名
         */
        public String getClazz() {
            return clazz;
        }

        /**
         * 设置数据源实现类的完整类名
         *
         * @param clazz 类名
         */
        public void setClazz(String clazz) {
            this.clazz = clazz;
        }

        /**
         * 获取数据源名称
         *
         * @return 数据源名称
         */
        public String getName() {
            return name;
        }

        /**
         * 设置数据源名称
         *
         * @param name 数据源名称
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取数据源描述
         *
         * @return 数据源描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 设置数据源描述
         *
         * @param description 数据源描述
         */
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

    /**
     * URL映射配置内部类
     *
     * 表示URL到数据源的映射关系，包括基础URL和对应的数据源ID。
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UrlMapping {
        /** 基础URL */
        @XmlAttribute
        private String baseUrl;

        /** 数据源ID */
        @XmlAttribute
        private String datasource;

        /**
         * 获取基础URL
         *
         * @return 基础URL
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置基础URL
         *
         * @param baseUrl 基础URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * 获取数据源ID
         *
         * @return 数据源ID
         */
        public String getDatasource() {
            return datasource;
        }

        /**
         * 设置数据源ID
         *
         * @param datasource 数据源ID
         */
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