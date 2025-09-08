# bfzy.tv 数据源插件

本项目是为 [电影数据获取服务](../README.md) 开发的 bfzy.tv 数据源插件。

## 项目结构

```
bfzy/
├── src/main/java/com/example/bfzy/
│   ├── BfzyMovieService.java    # bfzy.tv数据源实现类
│   ├── Movie.java               # 电影数据模型
│   └── MovieService.java        # 服务接口定义
├── pom.xml                      # Maven配置文件
└── README.md                    # 项目说明文档
```

## 数据源实现

### BfzyMovieService 类

[BfzyMovieService.java](src/main/java/com/example/bfzy/BfzyMovieService.java) 是 bfzy.tv 数据源的主要实现类，提供了以下功能：

1. **搜索电影**: 根据关键词搜索电影信息
2. **获取剧集**: 获取指定电影的所有剧集信息
3. **解析M3U8**: 解析指定剧集的M3U8播放地址

该类使用了 Jsoup 库来解析HTML页面，并通过正则表达式提取所需信息。

### Movie 类

[Movie.java](src/main/java/com/example/bfzy/Movie.java) 是电影数据模型，包含以下字段：

- `name`: 电影名称
- `description`: 电影描述
- `playUrl`: 播放页面地址
- `poster`: 海报图片地址
- `episodeList`: 剧集列表

其中 [Episode](src/main/java/com/example/bfzy/Movie.java#L176-L210) 是剧集信息的内部类，包含：
- `title`: 剧集标题
- `episodeUrl`: 剧集播放地址

### MovieService 接口

[MovieService.java](src/main/java/com/example/bfzy/MovieService.java) 定义了数据源需要实现的接口方法：

1. `searchMovies`: 搜索电影
2. `getEpisodes`: 获取剧集列表
3. `getM3u8Url`: 获取M3U8播放地址

## 构建项目

使用 Maven 构建项目：

```bash
cd bfzy
mvn clean package
```

构建完成后，会在 `target` 目录下生成 JAR 文件。

## 部署插件

1. 将生成的 JAR 文件复制到主项目的 [libs](../libs/) 目录中
2. 在主项目的 [movie-data-config.xml](../movie-data-config.xml) 配置文件中添加数据源配置：

```xml
<datasource id="bfzy" class="com.example.bfzy.BfzyMovieService">
    <name>BFZY数据源</name>
    <description>从bfzy.tv网站获取电影数据</description>
</datasource>

<urlMapping baseUrl="https://bfzy.tv" datasource="bfzy"/>
```

3. 启动主项目即可使用 bfzy.tv 数据源