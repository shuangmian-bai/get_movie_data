package org.example.get_movie_data.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;

@Component
public class SystemMonitorService {

    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    /**
     * 定时输出系统资源使用情况
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void logSystemStatus() {
        // 获取内存信息
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        // 计算堆内存使用情况
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        long heapCommitted = heapUsage.getCommitted();
        double heapUsedMB = heapUsed / (1024.0 * 1024.0);
        double heapMaxMB = heapMax / (1024.0 * 1024.0);
        double heapCommittedMB = heapCommitted / (1024.0 * 1024.0);
        double heapUsagePercentage = (heapMax > 0) ? (heapUsed * 100.0 / heapMax) : 0;

        // 计算非堆内存使用情况
        long nonHeapUsed = nonHeapUsage.getUsed();
        long nonHeapCommitted = nonHeapUsage.getCommitted();
        double nonHeapUsedMB = nonHeapUsed / (1024.0 * 1024.0);
        double nonHeapCommittedMB = nonHeapCommitted / (1024.0 * 1024.0);

        // 获取线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        // 输出日志
        System.out.println("======= 系统资源使用情况 =======");
        System.out.println("堆内存使用: " + decimalFormat.format(heapUsedMB) + "MB / " + 
                          decimalFormat.format(heapMaxMB) + "MB (" + 
                          decimalFormat.format(heapUsagePercentage) + "%)");
        System.out.println("堆内存已提交: " + decimalFormat.format(heapCommittedMB) + "MB");
        System.out.println("非堆内存使用: " + decimalFormat.format(nonHeapUsedMB) + "MB");
        System.out.println("非堆内存已提交: " + decimalFormat.format(nonHeapCommittedMB) + "MB");
        System.out.println("线程数: 当前=" + threadCount + ", 峰值=" + peakThreadCount + 
                          ", 总启动=" + totalStartedThreadCount);
        System.out.println("===============================");
    }
}