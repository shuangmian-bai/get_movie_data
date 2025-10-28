package org.example.get_movie_data.config;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.util.AnnotationScanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 自动配置生成器
 * 
 * 扫描libs目录下的JAR文件和主项目中的类，查找带有@DataSource注解的类，
 * 并自动生成对应的XML配置文件
 */
public class AutoConfigGenerator {
    
    private static final Logger logger = Logger.getLogger(AutoConfigGenerator.class.getName());
    
    private static final String LIBS_DIR = "libs";
    private static final String CONFIG_FILE = "config/movie-data-config.xml";
    
    /**
     * 生成自动配置文件
     */
    public static void generateConfig() {
        logger.info("Starting auto config generation...");
        
        try {
            // 创建DOM文档
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            // 创建根元素
            Element rootElement = doc.createElement("configuration");
            doc.appendChild(rootElement);
            
            // 创建datasources元素
            Element datasourcesElement = doc.createElement("datasources");
            rootElement.appendChild(datasourcesElement);
            
            // 创建urlMappings元素
            Element urlMappingsElement = doc.createElement("urlMappings");
            rootElement.appendChild(urlMappingsElement);
            
            // 扫描主项目和JAR文件查找数据源
            Map<String, DataSourceInfo> uniqueDataSources = new LinkedHashMap<>();
            scanMainProjectForDataSources(uniqueDataSources);
            scanJarsForDataSources(uniqueDataSources);
            
            // 将去重后的数据源添加到XML文档中
            for (DataSourceInfo dataSourceInfo : uniqueDataSources.values()) {
                // 创建datasource元素
                Element datasourceElement = doc.createElement("datasource");
                datasourceElement.setAttribute("id", dataSourceInfo.id);
                datasourceElement.setAttribute("class", dataSourceInfo.className);
                
                Element nameElement = doc.createElement("name");
                nameElement.setTextContent(dataSourceInfo.name);
                datasourceElement.appendChild(nameElement);
                
                Element descriptionElement = doc.createElement("description");
                descriptionElement.setTextContent(dataSourceInfo.description);
                datasourceElement.appendChild(descriptionElement);
                
                datasourcesElement.appendChild(datasourceElement);
                
                // 如果有baseUrl，创建urlMapping元素
                if (!dataSourceInfo.baseUrl.isEmpty()) {
                    Element urlMappingElement = doc.createElement("urlMapping");
                    urlMappingElement.setAttribute("baseUrl", dataSourceInfo.baseUrl);
                    urlMappingElement.setAttribute("datasource", dataSourceInfo.id);
                    urlMappingsElement.appendChild(urlMappingElement);
                }
            }
            
            // 添加通配符匹配作为默认数据源
            Element urlMappingElement = doc.createElement("urlMapping");
            urlMappingElement.setAttribute("baseUrl", "*");
            urlMappingElement.setAttribute("datasource", "internal");
            urlMappingsElement.appendChild(urlMappingElement);
            
            // 写入文件
            writeConfigToFile(doc);
            
            logger.info("Auto config generation completed successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating auto config", e);
        }
    }
    
    /**
     * 扫描主项目查找数据源注解
     */
    private static void scanMainProjectForDataSources(Map<String, DataSourceInfo> uniqueDataSources) {
        try {
            // 使用AnnotationScanner查找主项目中的注解类
            Set<Class<?>> annotatedClasses = AnnotationScanner.findAllAnnotatedClasses();
            
            for (Class<?> clazz : annotatedClasses) {
                DataSource dataSourceAnnotation = clazz.getAnnotation(DataSource.class);
                
                if (dataSourceAnnotation != null) {
                    String datasourceId = dataSourceAnnotation.id();
                    String className = clazz.getName();
                    
                    // 检查是否已处理过此ID或类名
                    if (uniqueDataSources.containsKey(datasourceId)) {
                        logger.info("Skipping duplicate datasource ID: " + datasourceId);
                        continue;
                    }
                    
                    if (containsClass(uniqueDataSources, className)) {
                        logger.info("Skipping duplicate datasource class: " + className);
                        continue;
                    }
                    
                    uniqueDataSources.put(datasourceId, new DataSourceInfo(
                        datasourceId,
                        className,
                        dataSourceAnnotation.name(),
                        dataSourceAnnotation.description(),
                        dataSourceAnnotation.baseUrl()
                    ));
                    logger.info("Found DataSource annotation in class: " + clazz.getName());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error scanning main project for data sources", e);
        }
    }
    
    /**
     * 扫描JAR文件查找数据源注解
     */
    private static void scanJarsForDataSources(Map<String, DataSourceInfo> uniqueDataSources) {
        File libDir = new File(LIBS_DIR);
        if (!libDir.exists()) {
            logger.warning("Lib directory does not exist: " + LIBS_DIR);
            return;
        }
        
        File[] jarFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            logger.info("No JAR files found in lib directory");
            return;
        }
        
        for (File jarFile : jarFiles) {
            scanJarForDataSources(jarFile, uniqueDataSources);
        }
    }
    
    /**
     * 扫描单个JAR文件查找数据源注解
     */
    private static void scanJarForDataSources(File jarFile, Map<String, DataSourceInfo> uniqueDataSources) {
        try (JarFile jar = new JarFile(jarFile)) {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()});
            
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace('\\', '.')
                            .replace(".class", "");
                    
                    try {
                        Class<?> clazz = Class.forName(className, false, classLoader);
                        DataSource dataSourceAnnotation = clazz.getAnnotation(DataSource.class);
                        
                        if (dataSourceAnnotation != null) {
                            String datasourceId = dataSourceAnnotation.id();
                            
                            // 检查是否已处理过此ID或类名
                            if (uniqueDataSources.containsKey(datasourceId)) {
                                logger.info("Skipping duplicate datasource ID from JAR: " + datasourceId);
                                continue;
                            }
                            
                            if (containsClass(uniqueDataSources, className)) {
                                logger.info("Skipping duplicate datasource class from JAR: " + className);
                                continue;
                            }
                            
                            uniqueDataSources.put(datasourceId, new DataSourceInfo(
                                datasourceId,
                                className,
                                dataSourceAnnotation.name(),
                                dataSourceAnnotation.description(),
                                dataSourceAnnotation.baseUrl()
                            ));
                            logger.info("Found DataSource annotation in class: " + className);
                        }
                    } catch (Exception e) {
                        logger.log(Level.FINE, "Could not load class: " + className, e);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading JAR file: " + jarFile.getName(), e);
        }
    }
    
    /**
     * 检查是否已包含指定类名的数据源
     */
    private static boolean containsClass(Map<String, DataSourceInfo> uniqueDataSources, String className) {
        for (DataSourceInfo info : uniqueDataSources.values()) {
            if (info.className.equals(className)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 将配置写入文件
     */
    private static void writeConfigToFile(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("indent", "yes");
        
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(CONFIG_FILE));
        
        transformer.transform(source, result);
        logger.info("Config file written to: " + CONFIG_FILE);
    }
    
    /**
     * 数据源信息内部类
     */
    private static class DataSourceInfo {
        final String id;
        final String className;
        final String name;
        final String description;
        final String baseUrl;
        
        DataSourceInfo(String id, String className, String name, String description, String baseUrl) {
            this.id = id;
            this.className = className;
            this.name = name;
            this.description = description;
            this.baseUrl = baseUrl;
        }
    }
    
    /**
     * 主方法，用于测试自动配置生成
     */
    public static void main(String[] args) {
        generateConfig();
    }
}