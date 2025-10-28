package org.example.get_movie_data;

import org.example.get_movie_data.config.AutoConfigGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@EnableScheduling
public class GetMovieDataApplication {

    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public static void main(String[] args) {
        // 确保在应用启动前config目录和必要的配置文件存在
        ensureConfigDirectoryAndFilesExist();
        
        // 只有在配置文件不存在时才生成自动配置
        File configFile = Paths.get("config", "movie-data-config.xml").toFile();
        if (!configFile.exists()) {
            // 生成自动配置
            AutoConfigGenerator.generateConfig();
        }
        
        SpringApplication.run(GetMovieDataApplication.class, args);
    }
    
    /**
     * 定期打印系统资源使用情况
     */
    @Scheduled(fixedRate = 60000) // 每分钟打印一次
    public void printSystemResourceUsage() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            
            // 获取堆内存信息
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
            long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024); // MB
            long heapCommitted = memoryBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024); // MB
            double heapUsagePercent = (double) heapUsed / heapMax * 100;
            
            // 获取非堆内存信息
            long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
            long nonHeapCommitted = memoryBean.getNonHeapMemoryUsage().getCommitted() / (1024 * 1024); // MB
            
            // 获取线程信息
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();
            long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();
            
            // 计算运行时间
            long uptime = System.currentTimeMillis() - startTime.get();
            long hours = uptime / (1000 * 60 * 60);
            long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
            long seconds = (uptime % (1000 * 60)) / 1000;
            
            // 打印系统资源使用情况
            System.out.println("======= 系统资源使用情况 =======");
            System.out.println("运行时间: " + hours + "小时 " + minutes + "分钟 " + seconds + "秒");
            System.out.println("堆内存使用: " + heapUsed + "MB / " + heapMax + "MB (" + DF.format(heapUsagePercent) + "%)");
            System.out.println("堆内存已提交: " + heapCommitted + "MB");
            System.out.println("非堆内存使用: " + nonHeapUsed + "MB");
            System.out.println("非堆内存已提交: " + nonHeapCommitted + "MB");
            System.out.println("线程数: 当前=" + threadCount + ", 峰值=" + peakThreadCount + ", 总启动=" + totalStartedThreadCount);
            System.out.println("===============================");
        } catch (Exception e) {
            System.err.println("Failed to print system resource usage: " + e.getMessage());
        }
    }
    
    /**
     * 确保config目录和配置文件存在
     */
    private static void ensureConfigDirectoryAndFilesExist() {
        try {
            // 创建config目录
            File configDir = Paths.get("config").toFile();
            if (!configDir.exists()) {
                boolean created = configDir.mkdirs();
                System.out.println("Config directory " + (created ? "created" : "already exists") + ": " + configDir.getAbsolutePath());
            } else {
                System.out.println("Config directory already exists: " + configDir.getAbsolutePath());
            }
            
            // 检查配置文件是否存在，如果不存在则从JAR内部复制
            File configFile = Paths.get("config", "movie-data-config.xml").toFile();
            if (!configFile.exists()) {
                System.out.println("Config file not found, copying from internal resources...");
                copyInternalConfigFile(configFile);
            } else {
                System.out.println("Config file already exists: " + configFile.getAbsolutePath());
            }
            
            // 创建libs目录
            File libsDir = Paths.get("libs").toFile();
            if (!libsDir.exists()) {
                boolean created = libsDir.mkdirs();
                System.out.println("Libs directory " + (created ? "created" : "already exists") + ": " + libsDir.getAbsolutePath());
            } else {
                System.out.println("Libs directory already exists: " + libsDir.getAbsolutePath());
            }
            
            // 创建cache目录
            File cacheDir = Paths.get("cache").toFile();
            if (!cacheDir.exists()) {
                boolean created = cacheDir.mkdirs();
                System.out.println("Cache directory " + (created ? "created" : "already exists") + ": " + cacheDir.getAbsolutePath());
            } else {
                System.out.println("Cache directory already exists: " + cacheDir.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create config directory or copy config file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从JAR内部复制配置文件到外部目录
     * 
     * @param targetFile 目标文件
     */
    private static void copyInternalConfigFile(File targetFile) {
        try {
            ClassPathResource resource = new ClassPathResource("config/movie-data-config.xml");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Files.copy(
                        inputStream,
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    );
                    System.out.println("Successfully copied internal config file to: " + targetFile.getAbsolutePath());
                }
            } else {
                System.out.println("Internal config file not found in JAR");
            }
        } catch (Exception e) {
            System.err.println("Failed to copy internal config file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}