# 电影数据获取服务

一个基于Spring Boot的可扩展电影数据获取服务，支持通过外部JAR插件扩展数据源。

## 项目介绍

本项目提供一个RESTful API服务，用于获取电影数据。其核心特点是支持通过外部JAR插件扩展数据源，无需修改主项目代码即可添加新的数据源实现。

### 主要功能

- 提供统一的电影数据访问接口
- 支持通过配置文件加载和管理数据源
- 支持自定义数据源插件化扩展
- 支持URL到数据源的灵活映射

## 技术架构

- **后端框架**: Spring Boot 3.5.5
- **构建工具**: Maven
- **Java版本**: JDK 17
- **配置格式**: XML

## 项目结构

```
get_movie_data/
├── src/main/java/org/example/get_movie_data/
│   ├── GetMovieDataApplication.java    # Spring Boot启动类
│   ├── controller/                     # 控制器层
│   ├── model/                          # 数据模型
│   ├── service/                        # 业务逻辑层
│   └── config/                         # 配置加载
├── src/main/resources/
│   └── config/
│       └── movie-data-config.xml       # 数据源配置文件
├── custom-datasource/                  # 自定义数据源示例项目
├── libs/                               # 外部数据源JAR包存放目录
├── pom.xml                             # 主项目Maven配置
└── README.md                           # 项目说明文档
```

## 快速开始

### 1. 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd get_movie_data

# 构建主项目
./mvnw clean package
```

### 2. 创建自定义数据源

参考 [custom-datasource](custom-datasource) 目录中的示例项目，创建你自己的数据源实现。

关键步骤：
1. 创建一个实现电影数据获取逻辑的类
2. 确保该类包含以下方法：
   - `List<Movie> searchMovies(String baseUrl, String keyword)`
   - `List<Movie.Episode> getEpisodes(String baseUrl, String playUrl)`
   - `String getM3u8Url(String baseUrl, String episodeUrl)`
3. 使用Maven打包成JAR文件

### 3. 部署自定义数据源

1. 将生成的JAR文件复制到 [libs](file:///C:/Users/Administrator/Desktop/get_movie_data/libs) 目录
2. 修改 [src/main/resources/config/movie-data-config.xml](file:///C:/Users/Administrator/Desktop/get_movie_data/src/main/resources/config/movie-data-config.xml) 配置文件：
   - 在 `<datasources>` 部分添加新的数据源配置
   - 在 `<urlMappings>` 部分添加URL到数据源的映射

示例配置：
```xml
<datasources>
    <!-- 其他数据源... -->
    <datasource id="my_datasource" class="com.example.myproject.MyMovieService">
        <name>我的数据源</name>
        <description>自定义电影数据源实现</description>
    </datasource>
</datasources>

<urlMappings>
    <!-- 其他映射... -->
    <urlMapping baseUrl="https://mydatasource.example.com/" datasource="my_datasource"/>
</urlMappings>
```

### 4. 运行服务

```bash
java -jar target/get_movie_data-0.0.1-SNAPSHOT.jar
```

### 5. 使用API

服务启动后，可通过以下API访问：

- 搜索电影: `GET /api/movie/search?baseUrl={baseUrl}&keyword={keyword}`
- 获取剧集: `GET /api/movie/episodes?baseUrl={baseUrl}&playUrl={playUrl}`
- 获取M3U8地址: `GET /api/movie/m3u8?baseUrl={baseUrl}&episodeUrl={episodeUrl}`

## 自定义数据源开发指南

### 数据模型

自定义数据源需要实现与主项目兼容的数据模型：

#### Movie类
```java
public class Movie {
    private String name;           // 电影名称
    private String description;    // 描述
    private boolean finished;      // 是否完结
    private String playUrl;        // 播放地址
    private int episodes;          // 集数
    private List<Episode> episodeList; // 剧集列表
    
    // getters and setters
}
```

#### Episode类
```java
public class Episode {
    private String title;       // 剧集标题
    private String episodeUrl;  // 剧集播放地址
    
    // getters and setters
}
```

### 接口方法

自定义数据源类需要实现以下方法：

1. **搜索电影**
   ```java
   public List<Movie> searchMovies(String baseUrl, String keyword)
   ```
   
2. **获取剧集**
   ```java
   public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl)
   ```
   
3. **获取M3U8地址**
   ```java
   public String getM3u8Url(String baseUrl, String episodeUrl)
   ```

### 示例实现

参考 [custom-datasource](custom-datasource) 目录中的示例项目了解完整实现。

## 配置说明

主配置文件位于 [src/main/resources/config/movie-data-config.xml](file:///C:/Users/Administrator/Desktop/get_movie_data/src/main/resources/config/movie-data-config.xml)，包含数据源和URL映射配置。

### 数据源配置
```xml
<datasource id="数据源ID" class="完整的类名">
    <name>数据源名称</name>
    <description>数据源描述</description>
</datasource>
```

### URL映射配置
```xml
<urlMapping baseUrl="基础URL" datasource="数据源ID"/>
```

支持通配符`*`匹配所有未明确指定的URL。

## 许可证

[MIT License](LICENSE)