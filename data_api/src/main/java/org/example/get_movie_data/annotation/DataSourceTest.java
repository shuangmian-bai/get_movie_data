package org.example.get_movie_data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源测试注解
 * 
 * 用于标识测试类，指定要测试的数据源
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSourceTest {
    
    /**
     * 要测试的数据源ID
     * 
     * @return 数据源ID
     */
    String value();
    
    /**
     * 测试名称
     * 
     * @return 测试名称
     */
    String name() default "";
    
    /**
     * 测试描述
     * 
     * @return 测试描述
     */
    String description() default "";
}