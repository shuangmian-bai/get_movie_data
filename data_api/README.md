# 电影数据获取服务

这是一个可扩展的电影数据获取服务，支持通过注解扩展数据源。该服务提供统一的RESTful API接口，能够从多个不同的电影网站获取电影信息、剧集列表和播放地址。

## 目录

- [功能特性](#功能特性)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
  - [环境要求](#环境要求)
  - [构建项目](#构建项目)
  - [运行应用](#运行应用)
- [API接口说明](#api接口说明)
- [扩展开发](#扩展开发)
  - [创建自定义数据源](#创建自定义数据源)
  - [现有数据源插件](#现有数据源插件)
- [常见问题](#常见问题)

## 功能特性

- **统一API接口**: 提供RESTful API接口，统一访问不同数据源的电影数据
- **注解驱动架构**: 支持通过@DataSource注解扩展数据源，易于添加新的电影网站支持
- **并发处理**: 支持并发从多个数据源获取数据，提高查询效率
- **缓存机制**: 内置缓存功能，减少重复请求，提高响应速度

## 系统架构

```mermaid
graph TD
    A[客户端] --> B[API网关]
    B --> C{MovieController}
    C --> D[MovieServiceManager]
    D --> E[CacheManager]
    D --> F[AnnotationScanner]
    F --> G[带@DataSource注解的类]
```

## 快速开始

### 环境要求

- Java 17 或更高版本
- Maven 3.6 或更高版本

### 构建项目

使用Maven Wrapper构建整个项目：

```bash
./mvnw clean package
```

执行该命令将在 `target` 目录下生成以下内容：
- `get_movie_data-0.0.1-SNAPSHOT.jar` - 主程序jar文件
- `config/` - 配置文件目录

### 运行应用

```bash
java -jar target/get_movie_data-0.0.1-SNAPSHOT.jar
```

应用启动后，默认在8080端口提供HTTP服务。

## API接口说明

### 搜索电影

```
POST /api/movie/search/all
Content-Type: application/json
```

请求体(JSON格式)：
```json
{
  "keyword": "搜索关键词"
}
```

并发从所有配置的数据源搜索电影，返回电影列表。

### 获取剧集列表

```
POST /api/movie/episodes
Content-Type: application/json
```

请求体(JSON格式)：
```json
{
  "baseUrl": "基础URL",
  "playUrl": "播放地址"
}
```

根据基础URL确定数据源，获取指定电影的所有剧集信息。

### 获取M3U8播放地址

```
POST /api/movie/m3u8
Content-Type: application/json
```

请求体(JSON格式)：
```json
{
  "baseUrl": "基础URL",
  "episodeUrl": "剧集地址"
}
```

根据基础URL确定数据源，获取指定剧集的M3U8播放地址。

## 扩展开发

### 创建自定义数据源

1. 实现 [MovieService](src/main/java/org/example/get_movie_data/service/MovieService.java) 接口
2. 使用 [@DataSource](src/main/java/org/example/get_movie_data/annotation/DataSource.java) 注解标记类
3. 将类放在 `org.example.get_movie_data.datasource` 包下
4. 系统会自动扫描并注册该数据源

示例：
```java
@DataSource(
    id = "mydatasource",
    name = "我的数据源",
    description = "描述信息",
    baseUrl = "https://my-site.com"
)
public class MyMovieService implements MovieService {
    // 实现接口方法
}
```

### 现有数据源插件

项目包含以下现成的数据源插件：

- 云云TV - 从云云TV网站获取电影数据
- 暴风影音 - 从bfzy.tv网站获取电影数据
- 茶杯狐 - 从茶杯狐网站获取电影数据
- 内部数据源 - 直接在主项目中定义的数据源示例

## 常见问题

### Q: 如何添加自定义数据源？

A: 实现MovieService接口，使用@DataSource注解标记类，放在org.example.get_movie_data.datasource包下，系统会自动扫描并注册。

### Q: 如何查看API文档？

A: 应用启动后，访问 `http://Host:Port/swagger-ui/index.html` 查看Swagger API文档。

### Q: 谁负责维护这个项目？

A: 该项目由[双面](https://github.com/shuangmian-bai/get_movie_data)维护。