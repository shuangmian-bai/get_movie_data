package org.example.get_movie_data;

import org.example.get_movie_data.annotation.DataSourceTest;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

@SpringBootTest
public class TestRunner {

    @Test
    public void runAllDataSourceTests() {
        try {
            // 使用Reflections库扫描所有带有@DataSourceTest注解的测试类
            Reflections reflections = new Reflections("org.example.get_movie_data.datasource");
            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(DataSourceTest.class);
            
            System.out.println("发现 " + annotatedClasses.size() + " 个数据源测试类:");
            
            for (Class<?> clazz : annotatedClasses) {
                DataSourceTest annotation = clazz.getAnnotation(DataSourceTest.class);
                System.out.println("数据源ID: " + annotation.value());
                System.out.println("测试类名: " + clazz.getSimpleName());
                System.out.println("测试名称: " + annotation.name());
                System.out.println("测试描述: " + annotation.description());
                System.out.println("------------------------");
            }
        } catch (Exception e) {
            System.err.println("扫描测试类时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}