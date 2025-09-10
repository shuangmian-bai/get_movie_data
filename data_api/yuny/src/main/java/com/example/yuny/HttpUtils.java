package com.example.yuny;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.zip.GZIPInputStream;

/**
 * HTTP工具类
 * 用于发送HTTP请求并获取响应内容
 */
public class HttpUtils {
    
    static {
        // 在静态初始化块中配置SSL信任所有证书
        // 注意：这只应在测试环境中使用，生产环境中应正确配置证书
        trustAllCertificates();
    }
    
    /**
     * 发送GET请求获取网页内容
     * 
     * @param url 请求地址
     * @return 网页内容字符串
     */
    public static String get(String url) {
        try {
            System.out.println("开始发送HTTP GET请求: " + url);
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setConnectTimeout(10000); // 连接超时10秒
            connection.setReadTimeout(15000);   // 读取超时15秒
            
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP响应码: " + responseCode);
            
            // 检查内容是否经过gzip压缩
            String contentEncoding = connection.getHeaderField("Content-Encoding");
            System.out.println("Content-Encoding: " + contentEncoding);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                
                // 如果内容经过gzip压缩，则解压缩
                if ("gzip".equalsIgnoreCase(contentEncoding)) {
                    inputStream = new GZIPInputStream(inputStream);
                    System.out.println("检测到gzip压缩内容，正在解压缩...");
                }
                
                // 读取内容
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder content = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                
                reader.close();
                System.out.println("成功获取内容，长度: " + content.length());
                return content.toString();
            } else {
                // 尝试读取错误流中的内容
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                    StringBuilder errorContent = new StringBuilder();
                    String errorLine;
                    
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorContent.append(errorLine).append("\n");
                    }
                    
                    errorReader.close();
                    System.err.println("HTTP请求失败，响应码: " + responseCode + "，错误内容: " + errorContent.toString());
                } else {
                    System.err.println("HTTP请求失败，响应码: " + responseCode + "，无错误详情");
                }
                return "";
            }
        } catch (Exception e) {
            System.err.println("HTTP请求异常: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * 配置SSL信任所有证书
     * 注意：这只应在测试环境中使用
     */
    private static void trustAllCertificates() {
        try {
            // 创建信任所有证书的TrustManager
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            // 获取SSL上下文并初始化
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // 设置默认的SSL套接字工厂
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            // 创建并设置信任所有主机名的Verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            
            // 设置默认的主机名验证器
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}