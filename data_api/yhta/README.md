# YHTA 数据源插件

这是一个用于 [get_movie_data](../README.md) 项目的外部数据源插件，用于解析 [https://www.yhta.cc/](https://www.yhta.cc/) 网站的电影数据。

## 项目结构

```
yhta/
├── src/main/java/com/example/yhta/
│   ├── YhtaMovieService.java    # YHTA电影服务实现类
│   ├── Movie.java               # 电影数据模型类
│   └── MovieService.java        # 服务接口定义
├── src/test/java/com/example/yhta/
│   └── YhtaMovieServiceTest.java # YHTA电影服务测试类
├── pom.xml                      # Maven配置文件
└── README.md                    # 项目说明文档
```

## 功能实现

本插件实现了以下核心功能：

1. **搜索电影** - 根据关键词搜索电影
2. **获取剧集** - 获取指定电影的所有剧集信息
3. **解析播放地址** - 解析具体剧集的M3U8播放地址

## 使用方法

1. 编译打包项目：
   ```bash
   mvn clean package
   ```

2. 将生成的 JAR 文件复制到主项目的 [libs](file:///C:/Users/Administrator/Desktop/get_movie_data/data_api/libs) 目录中

3. 在主项目的配置文件中添加数据源配置：
   ```xml
   <datasource>
       <url>https://www.yhta.cc/</url>
       <className>com.example.yhta.YhtaMovieService</className>
   </datasource>
   ```

## 测试

项目包含测试类 [YhtaMovieServiceTest.java](file:///C:/Users/Administrator/Desktop/get_movie_data/data_api/yhta/src/test/java/com/example/yhta/YhtaMovieServiceTest.java) 用于验证服务功能：

1. 运行测试：
   ```bash
   mvn test
   ```

2. 或直接运行测试类：
   ```bash
   java com.example.yhta.YhtaMovieServiceTest
   ```

## API 接口说明

### searchMovies
```java
public List<Movie> searchMovies(String baseUrl, String keyword)
```
根据关键词搜索电影

参数：
- `baseUrl` - 基础URL
- `keyword` - 搜索关键词

返回：
- 电影列表

### getEpisodes
```java
public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl)
```
获取指定电影的所有剧集

参数：
- `baseUrl` - 基础URL
- `playUrl` - 电影播放地址

返回：
- 剧集列表

### getM3u8Url
```java
public String getM3u8Url(String baseUrl, String episodeUrl)
```
获取指定剧集的M3U8播放地址

参数：
- `baseUrl` - 基础URL
- `episodeUrl` - 剧集播放地址

返回：
- M3U8播放地址
```