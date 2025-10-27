package org.example.get_movie_data.util;

import org.example.get_movie_data.annotation.DataSource;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 注解扫描器
 * 
 * 用于扫描主项目中的@DataSource注解类
 */
public class AnnotationScanner {
    
    private static final Logger logger = Logger.getLogger(AnnotationScanner.class.getName());
    
    private static final String MAIN_PROJECT_PACKAGE = "org.example.get_movie_data";
    
    /**
     * 扫描主项目中带有@DataSource注解的类
     * 
     * @param datasourceId 数据源ID
     * @return 带有指定数据源ID的@DataSource注解的类名，如果未找到则返回null
     */
    public static String findAnnotatedClassInMainProject(String datasourceId) {
        try {
            // 获取所有带@DataSource注解的类
            Set<Class<?>> annotatedClasses = findAllAnnotatedClasses();
            
            // 检查每个类是否有匹配的注解
            for (Class<?> clazz : annotatedClasses) {
                DataSource dataSourceAnnotation = clazz.getAnnotation(DataSource.class);
                if (dataSourceAnnotation != null && datasourceId.equals(dataSourceAnnotation.id())) {
                    return clazz.getName();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error scanning main project for annotated classes", e);
        }
        
        return null;
    }
    
    /**
     * 查找所有带@DataSource注解的类
     * 
     * @return 带@DataSource注解的类集合
     */
    public static Set<Class<?>> findAllAnnotatedClasses() {
        Set<Class<?>> classes = new HashSet<>();
        
        try {
            // 使用Reflections库扫描指定包中的注解
            Reflections reflections = new Reflections(MAIN_PROJECT_PACKAGE, Scanners.TypesAnnotated);
            classes = reflections.getTypesAnnotatedWith(DataSource.class);
            logger.info("Found " + classes.size() + " DataSource annotated classes");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error finding all annotated classes", e);
        }
        
        return classes;
    }
}