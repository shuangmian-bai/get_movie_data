package org.example.get_movie_data.build;

import org.example.get_movie_data.annotation.DataSource;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 数据源打包器
 * 
 * 扫描主项目中带有@DataSource注解的类，并将它们打包成独立的JAR文件
 */
public class DataSourcePackager {
    
    private static final Logger logger = Logger.getLogger(DataSourcePackager.class.getName());
    
    private static final String MAIN_PROJECT_PACKAGE = "org.example.get_movie_data";
    private static final String LIBS_DIR = "libs";
    
    public static void main(String[] args) {
        if (args.length > 0 && "package".equals(args[0])) {
            packageDataSources();
        } else {
            System.out.println("Usage: DataSourcePackager package");
        }
    }
    
    /**
     * 打包数据源类
     */
    public static void packageDataSources() {
        try {
            // 确保libs目录存在
            Files.createDirectories(Paths.get(LIBS_DIR));
            
            // 查找所有带@DataSource注解的类
            Set<Class<?>> dataSourceClasses = findDataSourceAnnotatedClasses();
            
            // 为每个数据源类创建独立的JAR文件
            for (Class<?> clazz : dataSourceClasses) {
                createDataSourceJar(clazz);
            }
            
            System.out.println("数据源打包完成，共打包 " + dataSourceClasses.size() + " 个数据源");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "打包数据源时出错", e);
        }
    }
    
    /**
     * 查找所有带@DataSource注解的类
     * 
     * @return 带@DataSource注解的类集合
     */
    private static Set<Class<?>> findDataSourceAnnotatedClasses() {
        Set<Class<?>> classes = new HashSet<>();
        
        try {
            // 使用Reflections库扫描指定包中的注解
            Reflections reflections = new Reflections(MAIN_PROJECT_PACKAGE, Scanners.TypesAnnotated);
            classes = reflections.getTypesAnnotatedWith(DataSource.class);
            logger.info("找到 " + classes.size() + " 个带@DataSource注解的类");
            
            for (Class<?> clazz : classes) {
                logger.info("找到数据源类: " + clazz.getName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "查找带@DataSource注解的类时出错", e);
        }
        
        return classes;
    }
    
    /**
     * 为数据源类创建JAR文件
     * 
     * @param clazz 数据源类
     */
    private static void createDataSourceJar(Class<?> clazz) {
        try {
            String className = clazz.getName();
            String jarFileName = className.substring(className.lastIndexOf('.') + 1) + "-datasource.jar";
            Path jarPath = Paths.get(LIBS_DIR, jarFileName);
            
            logger.info("正在为类 " + className + " 创建JAR文件: " + jarPath);
            
            // 创建JAR文件
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
                // 添加类文件
                String classPath = className.replace('.', '/') + ".class";
                addClassToJar(jos, classPath, clazz);
                
                // 添加内部类（如果有）
                addInnerClassesToJar(jos, clazz);
            }
            
            logger.info("成功创建JAR文件: " + jarPath);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "为类 " + clazz.getName() + " 创建JAR文件时出错", e);
        }
    }
    
    /**
     * 将类添加到JAR文件
     * 
     * @param jos JAR输出流
     * @param classPath 类路径
     * @param clazz 类
     * @throws IOException IO异常
     */
    private static void addClassToJar(JarOutputStream jos, String classPath, Class<?> clazz) throws IOException {
        // 添加类文件到JAR
        JarEntry entry = new JarEntry(classPath);
        jos.putNextEntry(entry);
        
        // 获取类的字节码
        String classResource = classPath;
        URL resourceUrl = clazz.getClassLoader().getResource(classResource);
        if (resourceUrl != null) {
            byte[] classBytes = Files.readAllBytes(Paths.get(resourceUrl.getFile()));
            jos.write(classBytes);
        }
        
        jos.closeEntry();
        
        // 添加依赖的类（简化处理，实际项目中可能需要更复杂的依赖分析）
        addDependencyClasses(jos, clazz);
    }
    
    /**
     * 添加依赖类到JAR文件
     * 
     * @param jos JAR输出流
     * @param clazz 类
     * @throws IOException IO异常
     */
    private static void addDependencyClasses(JarOutputStream jos, Class<?> clazz) throws IOException {
        // 这里简化处理，实际项目中可能需要分析类的依赖关系
        // 添加一些常用的依赖类
        try {
            // 添加Movie类
            Class<?> movieClass = Class.forName("org.example.get_movie_data.model.Movie");
            String movieClassPath = movieClass.getName().replace('.', '/') + ".class";
            JarEntry entry = new JarEntry(movieClassPath);
            jos.putNextEntry(entry);
            URL resourceUrl = movieClass.getClassLoader().getResource(movieClassPath);
            if (resourceUrl != null) {
                byte[] classBytes = Files.readAllBytes(Paths.get(resourceUrl.getFile()));
                jos.write(classBytes);
            }
            jos.closeEntry();
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "找不到Movie类", e);
        }
    }
    
    /**
     * 添加内部类到JAR文件
     * 
     * @param jos JAR输出流
     * @param clazz 外部类
     * @throws IOException IO异常
     */
    private static void addInnerClassesToJar(JarOutputStream jos, Class<?> clazz) throws IOException {
        // 简化处理，实际项目中可能需要更复杂的内部类识别
        String className = clazz.getName();
        String classPath = className.replace('.', '/');
        
        // 查找可能的内部类文件
        String classDir = classPath.substring(0, classPath.lastIndexOf('/'));
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        
        // 这里简化处理，实际项目中可能需要扫描文件系统查找内部类
    }
}