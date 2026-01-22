package org.example.get_movie_data.util;

import org.example.get_movie_data.annotation.DataSource;
import org.example.get_movie_data.service.MovieService;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * 注解扫描工具类
 * 
 * 扫描指定包下带有@DataSource注解的类
 */
public class AnnotationScanner {
    
    /**
     * 扫描指定包下所有带@DataSource注解的MovieService实现类
     * 
     * @param packageName 包名
     * @return 带有@DataSource注解的类集合
     */
    public static Set<Class<?>> scanAnnotatedClasses(String packageName) {
        Set<Class<?>> annotatedClasses = new HashSet<>();
        
        try {
            // 获取包路径
            String packagePath = packageName.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File packageDir = new File(resource.getFile());
                
                if (packageDir.exists() && packageDir.isDirectory()) {
                    File[] classFiles = packageDir.listFiles((dir, name) -> name.endsWith(".class"));
                    
                    if (classFiles != null) {
                        for (File classFile : classFiles) {
                            String className = packageName + "." + 
                                classFile.getName().substring(0, classFile.getName().length() - 6); // 移除.class后缀
                            
                            try {
                                Class<?> clazz = Class.forName(className);
                                
                                // 检查是否是MovieService的实现类且带有@DataSource注解
                                if (MovieService.class.isAssignableFrom(clazz) && 
                                    clazz.isAnnotationPresent(DataSource.class) &&
                                    clazz != MovieService.class) {
                                    annotatedClasses.add(clazz);
                                }
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                // 忽略无法加载的类
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return annotatedClasses;
    }
    
    /**
     * 扫描主项目中的注解类
     * 
     * @return 带有@DataSource注解的类集合
     */
    public static Set<Class<?>> findAllAnnotatedClasses() {
        return scanAnnotatedClasses("org.example.get_movie_data.datasource");
    }
    
    /**
     * 根据数据源ID查找带注解的类
     * 
     * @param datasourceId 数据源ID
     * @return 类名，如果未找到则返回null
     */
    public static String findAnnotatedClassInMainProject(String datasourceId) {
        Set<Class<?>> annotatedClasses = findAllAnnotatedClasses();
        
        for (Class<?> clazz : annotatedClasses) {
            DataSource annotation = clazz.getAnnotation(DataSource.class);
            if (annotation != null && datasourceId.equals(annotation.id())) {
                return clazz.getName();
            }
        }
        
        return null;
    }
}