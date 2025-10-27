package org.example.get_movie_data.build;

import org.example.get_movie_data.annotation.DataSource;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 改进的数据源打包器
 * 
 * 扫描主项目中带有@DataSource注解的类，并将它们打包成独立的JAR文件
 */
public class ImprovedDataSourcePackager {
    
    private static final Logger logger = Logger.getLogger(ImprovedDataSourcePackager.class.getName());
    
    private static final String MAIN_PROJECT_PACKAGE = "org.example.get_movie_data";
    private static final String LIBS_DIR = "libs";
    private static final String CLASSES_DIR = "target/classes";
    
    public static void main(String[] args) {
        if (args.length > 0 && "package".equals(args[0])) {
            packageDataSources();
        } else {
            System.out.println("Usage: ImprovedDataSourcePackager package");
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
            Collection<URL> urls = ClasspathHelper.forPackage(MAIN_PROJECT_PACKAGE);
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(urls)
                    .setScanners(Scanners.TypesAnnotated));
            
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
            DataSource annotation = clazz.getAnnotation(DataSource.class);
            String jarFileName = annotation.id() + "-" + annotation.version() + ".jar";
            Path jarPath = Paths.get(LIBS_DIR, jarFileName);
            
            logger.info("正在为类 " + className + " 创建JAR文件: " + jarPath);
            
            // 创建JAR文件
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
                // 添加类文件
                String classPath = className.replace('.', '/') + ".class";
                addClassToJar(jos, classPath, clazz);
                
                // 添加内部类（如果有）
                addInnerClassesToJar(jos, clazz);
                
                // 添加依赖类
                addDependencyClasses(jos);
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
            byte[] classBytes = readBytesFromUrl(resourceUrl);
            jos.write(classBytes);
        }
        
        jos.closeEntry();
    }
    
    /**
     * 从URL读取字节数据
     * 
     * @param url URL
     * @return 字节数组
     * @throws IOException IO异常
     */
    private static byte[] readBytesFromUrl(URL url) throws IOException {
        // 处理文件URL，特别是Windows路径
        if ("file".equals(url.getProtocol())) {
            // 将URL转换为路径，处理Windows上的路径问题
            String path = url.getPath();
            // 如果路径以 / 开头并且包含驱动器字母，去掉开头的 /
            if (path.startsWith("/") && path.length() > 3 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            return Files.readAllBytes(Paths.get(path));
        }
        
        // 处理其他类型的URL
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }
    
    /**
     * 添加依赖类到JAR文件
     * 
     * @param jos JAR输出流
     * @throws IOException IO异常
     */
    private static void addDependencyClasses(JarOutputStream jos) throws IOException {
        // 添加Movie类
        try {
            Class<?> movieClass = Class.forName("org.example.get_movie_data.model.Movie");
            String movieClassPath = movieClass.getName().replace('.', '/') + ".class";
            JarEntry entry = new JarEntry(movieClassPath);
            jos.putNextEntry(entry);
            URL resourceUrl = movieClass.getClassLoader().getResource(movieClassPath);
            if (resourceUrl != null) {
                byte[] classBytes = readBytesFromUrl(resourceUrl);
                jos.write(classBytes);
            }
            jos.closeEntry();
            
            // 添加Episode类（Movie的内部类）
            Class<?> episodeClass = Class.forName("org.example.get_movie_data.model.Movie$Episode");
            String episodeClassPath = episodeClass.getName().replace('.', '/') + ".class";
            JarEntry episodeEntry = new JarEntry(episodeClassPath);
            jos.putNextEntry(episodeEntry);
            URL episodeResourceUrl = episodeClass.getClassLoader().getResource(episodeClassPath);
            if (episodeResourceUrl != null) {
                byte[] episodeClassBytes = readBytesFromUrl(episodeResourceUrl);
                jos.write(episodeClassBytes);
            }
            jos.closeEntry();
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "找不到Movie类", e);
        }
        
        // 添加DataSource注解类
        try {
            Class<?> dataSourceClass = Class.forName("org.example.get_movie_data.annotation.DataSource");
            String dataSourceClassPath = dataSourceClass.getName().replace('.', '/') + ".class";
            JarEntry entry = new JarEntry(dataSourceClassPath);
            jos.putNextEntry(entry);
            URL resourceUrl = dataSourceClass.getClassLoader().getResource(dataSourceClassPath);
            if (resourceUrl != null) {
                byte[] classBytes = readBytesFromUrl(resourceUrl);
                jos.write(classBytes);
            }
            jos.closeEntry();
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "找不到DataSource注解类", e);
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
        // 获取编译后的类文件目录
        String classFileName = clazz.getSimpleName() + "$";
        String classPathPrefix = clazz.getPackageName().replace('.', '/');
        
        try {
            // 遍历classes目录寻找内部类
            Path classesDir = Paths.get(CLASSES_DIR, classPathPrefix);
            if (Files.exists(classesDir) && Files.isDirectory(classesDir)) {
                Files.walk(classesDir)
                    .filter(path -> path.getFileName().toString().startsWith(classFileName))
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            String innerClassPath = classPathPrefix + "/" + path.getFileName().toString();
                            JarEntry entry = new JarEntry(innerClassPath);
                            jos.putNextEntry(entry);
                            byte[] classBytes = Files.readAllBytes(path);
                            jos.write(classBytes);
                            jos.closeEntry();
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "添加内部类到JAR时出错: " + path, e);
                        }
                    });
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "查找内部类时出错", e);
        }
    }
}