# 电影数据获取系统配置文档

## 1. 概述

本文档详细说明了电影数据获取系统的配置方法，包括如何配置数据源、URL映射以及如何扩展系统以支持新的数据源实现。

## 2. 配置文件结构

系统使用XML格式的配置文件，位于 `src/main/resources/config/movie-data-config.xml`。

配置文件包含两个主要部分：
- 数据源配置 (datasources)
- URL映射配置 (urlMappings)

## 3. 数据源配置

### 3.1 配置结构

```xml
<datasources>
    <datasource id="唯一标识" class="完整类名">
        <name>数据源名称</name>
        <description>数据源描述</description>
    </datasource>
</datasources>
```

### 3.2 配置项说明

| 配置项 | 必填 | 说明 |
|--------|------|------|
| id | 是 | 数据源的唯一标识符，用于在URL映射中引用 |
| class | 是 | 实现类的完整类名，系统将通过反射创建实例 |
| name | 是 | 数据源的可读名称 |
| description | 是 | 数据源的详细描述 |

### 3.3 示例配置

```xml
<datasources>
    <datasource id="default" class="org.example.get_movie_data.service.impl.MovieServiceImpl">
        <name>默认数据源</name>
        <description>默认的电影数据获取实现</description>
    </datasource>
    
    <datasource id="douban" class="org.example.get_movie_data.service.impl.DoubanMovieServiceImpl">
        <name>豆瓣数据源</name>
        <description>豆瓣电影数据获取实现</description>
    </datasource>
</datasources>
```

## 4. URL映射配置

### 4.1 配置结构

```xml
<urlMappings>
    <urlMapping baseUrl="基础URL" datasource="数据源ID"/>
</urlMappings>
```

### 4.2 配置项说明

| 配置项 | 必填 | 说明 |
|--------|------|------|
| baseUrl | 是 | 需要匹配的基础URL，支持通配符"*" |
| datasource | 是 | 对应的数据源ID，必须在datasources中定义 |

### 4.3 示例配置

```xml
<urlMappings>
    <!-- 默认映射，匹配所有未明确指定的URL -->
    <urlMapping baseUrl="*" datasource="default"/>
    
    <!-- 特定URL映射 -->
    <urlMapping baseUrl="https://movie.douban.com/" datasource="douban"/>
</urlMappings>
```

## 5. 扩展新的数据源

### 5.1 创建实现类

1. 创建新的Java类，实现 [MovieService](file:///C:/Users/Administrator/Desktop/%E5%BF%83%E7%90%86%E6%B5%8B%E8%AF%84web/get_movie_data/src/main/java/org/example/get_movie_data/service/MovieService.java#L9-L47) 接口
2. 实现所有接口方法：
   - [searchMovies](file:///C:/Users/Administrator/Desktop/%E5%BF%83%E7%90%86%E6%B5%8B%E8%AF%84web/get_movie_data/src/main/java/org/example/get_movie_data/service/MovieService.java#L17-L22)：搜索电影
   - [getEpisodes](file:///C:/Users/Administrator/Desktop/%E5%BF%83%E7%90%86%E6%B5%8B%E8%AF%84web/get_movie_data/src/main/java/org/example/get_movie_data/service/MovieService.java#L28-L33)：获取剧集列表
   - [getM3u8Url](file:///C:/Users/Administrator/Desktop/%E5%BF%83%E7%90%86%E6%B5%8B%E8%AF%84web/get_movie_data/src/main/java/org/example/get_movie_data/service/MovieService.java#L39-L43)：获取M3U8播放地址
   - [getMovieServiceByDatasource](file:///C:/Users/Administrator/Desktop/%E5%BF%83%E7%90%86%E6%B5%8B%E8%AF%84web/get_movie_data/src/main/java/org/example/get_movie_data/service/MovieService.java#L41-L46)：获取数据源服务

### 5.2 打包和部署

1. 将实现类打包为JAR文件
2. 将JAR文件放入项目的 `libs` 目录
3. 在配置文件中添加数据源配置

### 5.3 配置示例

假设我们创建了一个处理爱奇艺电影数据的实现类：

```java
package org.example.get_movie_data.service.impl;

public class IQIYIMovieServiceImpl implements MovieService {
    // 实现接口方法
}
```

打包为 `iqiyi-movie-service.jar` 并放入 `libs` 目录后，在配置文件中添加：

```xml
<datasources>
    <datasource id="iqiyi" class="org.example.get_movie_data.service.impl.IQIYIMovieServiceImpl">
        <name>爱奇艺数据源</name>
        <description>爱奇艺电影数据获取实现</description>
    </datasource>
</datasources>

<urlMappings>
    <urlMapping baseUrl="https://www.iqiyi.com/" datasource="iqiyi"/>
</urlMappings>
```

## 6. API使用说明

### 6.1 搜索电影

```
GET /api/movie/search?baseUrl={baseUrl}&keyword={keyword}[&datasource={datasource}]
```

参数说明：
- baseUrl: 基础URL
- keyword: 搜索关键词
- datasource: 可选，指定数据源ID

### 6.2 获取剧集列表

```
GET /api/movie/episodes?baseUrl={baseUrl}&playUrl={playUrl}[&datasource={datasource}]
```

参数说明：
- baseUrl: 基础URL
- playUrl: 播放地址
- datasource: 可选，指定数据源ID

### 6.3 获取M3U8地址

```
GET /api/movie/m3u8?baseUrl={baseUrl}&episodeUrl={episodeUrl}[&datasource={datasource}]
```

参数说明：
- baseUrl: 基础URL
- episodeUrl: 剧集播放地址
- datasource: 可选，指定数据源ID

## 7. 使用示例

### 7.1 使用默认数据源

```
GET /api/movie/search?baseUrl=https://example.com/&keyword=科幻电影
```

### 7.2 显式指定数据源

```
GET /api/movie/search?baseUrl=https://movie.douban.com/&keyword=科幻电影&datasource=douban
```

### 7.3 通过URL自动匹配数据源

当配置了URL映射后：
```xml
<urlMapping baseUrl="https://movie.douban.com/" datasource="douban"/>
```

以下请求将自动使用豆瓣数据源：

```
GET /api/movie/search?baseUrl=https://movie.douban.com/&keyword=科幻电影
```

## 8. 注意事项

1. 确保 `libs` 目录中的JAR文件与主程序兼容
2. 数据源实现类必须有公共的无参构造函数
3. URL映射按照配置顺序进行匹配，第一个匹配的规则将被使用
4. 当没有匹配的URL映射时，将使用通配符(*)规则