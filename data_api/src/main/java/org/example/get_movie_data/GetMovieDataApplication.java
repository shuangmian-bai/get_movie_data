package org.example.get_movie_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@EnableScheduling
public class GetMovieDataApplication {

    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public static void main(String[] args) {
        // 确保必要的目录存在
        ensureDirectoriesExist();
        
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
     * 确保必要的目录存在
     */
    private static void ensureDirectoriesExist() {
        try {
            // 创建cache目录
            File cacheDir = Paths.get("cache").toFile();
            if (!cacheDir.exists()) {
                boolean created = cacheDir.mkdirs();
                System.out.println("Cache directory " + (created ? "created" : "already exists") + ": " + cacheDir.getAbsolutePath());
            } else {
                System.out.println("Cache directory already exists: " + cacheDir.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create directories: " + e.getMessage());
            e.printStackTrace();
        }
    }
}