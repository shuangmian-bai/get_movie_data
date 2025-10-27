package org.example.get_movie_data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源注解
 * 
 * 用于标识一个类作为数据源实现，可以指定基础URL和其他元数据
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {
    
    /**
     * 数据源ID
     * 
     * @return 数据源ID
     */
    String id();
    
    /**
     * 数据源名称
     * 
     * @return 数据源名称
     */
    String name() default "";
    
    /**
     * 数据源描述
     * 
     * @return 数据源描述
     */
    String description() default "";
    
    /**
     * 基础URL
     * 
     * @return 基础URL
     */
    String baseUrl() default "";
    
    /**
     * 数据源版本
     * 
     * @return 数据源版本
     */
    String version() default "1.0.0";
}