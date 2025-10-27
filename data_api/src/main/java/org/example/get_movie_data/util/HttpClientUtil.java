package org.example.get_movie_data.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 统一的HTTP客户端工具类
 * 
 * 封装OkHttp客户端，提供统一的HTTP请求方法
 */
public class HttpClientUtil {
    
    private static final Logger logger = Logger.getLogger(HttpClientUtil.class.getName());
    
    // 创建OkHttp客户端实例
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    /**
     * 发送GET请求
     * 
     * @param url 请求URL
     * @return 响应内容字符串
     */
    public static String sendGetRequest(String url) {
        return sendGetRequest(url, null);
    }
    
    /**
     * 发送GET请求
     * 
     * @param url 请求URL
     * @param userAgent User-Agent头信息
     * @return 响应内容字符串
     */
    public static String sendGetRequest(String url, String userAgent) {
        try {
            // 构建请求
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .addHeader("Connection", "keep-alive");
            
            // 添加User-Agent
            if (userAgent != null && !userAgent.isEmpty()) {
                requestBuilder.addHeader("User-Agent", userAgent);
            } else {
                requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            }
            
            Request request = requestBuilder.build();

            // 执行请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("HTTP request failed with code: " + response.code() + ", url: " + url);
                    return "";
                }
                
                // 获取响应体
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    return responseBody.string();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending GET request to: " + url, e);
        }
        return "";
    }
}