# 第二个自定义数据源示例项目

这是一个第二个自定义数据源的示例项目，展示了如何创建可被主项目加载的外部数据源JAR包。

## 项目结构

```
custom-datasource2/
├── src/main/java/com/example/custom2/
│   ├── SecondaryMovieService.java    # 数据源实现类
│   └── Movie.java                    # 数据模型类
├── pom.xml                          # Maven配置文件
└── README.md                        # 项目说明文档
```

## 开发指南

### 1. 数据模型

项目包含两个核心数据模型类：

1. **Movie.java** - 电影信息模型
2. **Movie.Episode** - 剧集信息模型（内部类）

### 2. 核心实现类

**SecondaryMovieService.java** 包含以下核心方法：

1. `searchMovies(String baseUrl, String keyword)` - 搜索电影
2. `getEpisodes(String baseUrl, String playUrl)` - 获取剧集列表
3. `getM3u8Url(String baseUrl, String episodeUrl)` - 获取M3U8播放地址

### 3. 打包和部署

1. 使用Maven打包项目：
   ```bash
   cd custom-datasource2
   ../mvnw clean package
   ```

2. 将生成的JAR包复制到主项目的libs目录：
   ```bash
   cp target/secondary-movie-datasource-1.0.0.jar ../libs/
   ```

3. 在主项目的配置文件中添加数据源配置和URL映射

## 扩展开发

要创建自己的数据源，可以按照以下步骤进行：

1. 复制此项目作为模板
2. 修改包名和类名
3. 实现具体的电影数据获取逻辑
4. 打包并部署到主项目

## 注意事项

1. 确保数据模型类的结构与主项目兼容
2. 方法签名必须与示例保持一致
3. 不要在JAR包中包含主项目的类，避免类冲突
4. 使用独立的包名空间（如com.example.yourproject）