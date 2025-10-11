package org.example.get_movie_data.service;

import org.example.get_movie_data.controller.CastController;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 投屏服务类
 * 
 * 提供投屏相关服务，包括扫描局域网内可投屏设备等功能
 * 
 * @author get_movie_data team
 * @version 1.0.0
 */
@Service
public class CastService {
    
    private static final Logger logger = Logger.getLogger(CastService.class.getName());
    
    // 常见的投屏协议端口
    private static final int[] COMMON_CAST_PORTS = {80, 8008, 8009, 8443, 9000, 9090};
    
    // 常见的投屏服务标识
    private static final Map<Integer, String> PORT_SERVICE_MAP = new HashMap<>();
    
    static {
        PORT_SERVICE_MAP.put(80, "HTTP Service");
        PORT_SERVICE_MAP.put(8008, "Google Cast");
        PORT_SERVICE_MAP.put(8009, "Google Cast");
        PORT_SERVICE_MAP.put(8443, "HTTPS Service");
        PORT_SERVICE_MAP.put(9000, "DLNA");
        PORT_SERVICE_MAP.put(9090, "Custom Cast");
    }
    
    /**
     * 扫描局域网内可投屏设备
     * 
     * @param timeout 扫描超时时间（秒）
     * @return 设备列表
     */
    public List<CastController.CastDevice> scanDevices(int timeout) {
        logger.info("Starting device scan with timeout: " + timeout + " seconds");
        
        List<CastController.CastDevice> devices = new ArrayList<>();
        
        try {
            // 获取本机IP地址和子网
            String subnet = getLocalSubnet();
            if (subnet == null) {
                logger.warning("Unable to determine local subnet");
                return devices;
            }
            
            logger.info("Scanning subnet: " + subnet);
            
            // 创建线程池
            ExecutorService executor = Executors.newFixedThreadPool(50);
            List<Future<CastController.CastDevice>> futures = new ArrayList<>();
            
            // 扫描子网中的每个IP地址
            for (int i = 1; i < 255; i++) {
                String ip = subnet + "." + i;
                Future<CastController.CastDevice> future = executor.submit(() -> checkDevice(ip));
                futures.add(future);
            }
            
            // 收集结果
            for (Future<CastController.CastDevice> future : futures) {
                try {
                    CastController.CastDevice device = future.get(timeout, TimeUnit.SECONDS);
                    if (device != null) {
                        devices.add(device);
                    }
                } catch (TimeoutException e) {
                    logger.log(Level.WARNING, "Timeout checking device", e);
                    future.cancel(true);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error checking device", e);
                }
            }
            
            // 关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error scanning devices", e);
        }
        
        logger.info("Device scan completed. Found " + devices.size() + " devices");
        return devices;
    }
    
    /**
     * 获取本地子网地址
     * 
     * @return 子网地址（如192.168.1）
     */
    private String getLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // 跳过回环接口和禁用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // 只处理IPv4地址
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String ipAddress = address.getHostAddress();
                        // 获取子网（去掉最后一段）
                        int lastDotIndex = ipAddress.lastIndexOf('.');
                        if (lastDotIndex > 0) {
                            return ipAddress.substring(0, lastDotIndex);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.log(Level.WARNING, "Error getting local subnet", e);
        }
        
        return null;
    }
    
    /**
     * 检查指定IP地址是否为可投屏设备
     * 
     * @param ip IP地址
     * @return 设备信息，如果不是可投屏设备则返回null
     */
    private CastController.CastDevice checkDevice(String ip) {
        // 检查常见投屏端口
        for (int port : COMMON_CAST_PORTS) {
            if (isPortOpen(ip, port, 1000)) { // 1秒超时
                String serviceName = PORT_SERVICE_MAP.getOrDefault(port, "Unknown Service");
                String deviceName = getDeviceName(ip);
                
                CastController.CastDevice device = new CastController.CastDevice();
                device.setIp(ip);
                device.setName(deviceName);
                device.setType(serviceName);
                
                logger.info("Found cast device: " + ip + ":" + port + " (" + serviceName + ")");
                return device;
            }
        }
        
        return null;
    }
    
    /**
     * 检查指定IP和端口是否开放
     * 
     * @param ip IP地址
     * @param port 端口号
     * @param timeout 超时时间（毫秒）
     * @return 是否开放
     */
    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取设备名称
     * 
     * @param ip IP地址
     * @return 设备名称
     */
    private String getDeviceName(String ip) {
        // 尝试通过反向DNS查找获取主机名
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            String hostName = inetAddress.getHostName();
            if (!hostName.equals(ip)) {
                return hostName;
            }
        } catch (UnknownHostException e) {
            // 忽略异常，使用默认名称
        }
        
        // 默认使用IP地址作为名称
        return "Device-" + ip;
    }
}