# 自定义数据源JAR包模板

这是一个用于创建自定义数据源实现的目录模板。您可以基于此模板创建自己的数据源实现。

## 目录结构

```
datasource-template/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── yourcompany/
│       │           └── movies/
│       │               └── YourCustomMovieService.java
│       └── resources/
│           └── config/
│               └── movie-data-config.xml (可选)
├── pom.xml (可选，如果使用Maven构建)
├── build.gradle (可选，如果使用Gradle构建)
└── README.md (当前文件)
```

## Java实现类模板

在 `src/main/java/com/yourcompany/movies/` 目录下创建您的自定义数据源实现类：

### YourCustomMovieService.java

```java
package com.yourcompany.movies;

import org.example.get_movie_data.model.Movie;
import org.example.get_movie_data.service.MovieService;

import java.util.ArrayList;
import java.util.List;

public class YourCustomMovieService implements MovieService {
    
    @Override
    public List<Movie> searchMovies(String baseUrl, String keyword) {
        // 实现搜索电影逻辑
        List<Movie> movies = new ArrayList<>();
        
        // 示例数据
        Movie movie = new Movie();
        movie.setName("示例电影名称");
        movie.setDescription("示例电影描述");
        movie.setFinished(true);
        movie.setPlayUrl(baseUrl + "/play/123");
        movie.setEpisodes(10);
        
        movies.add(movie);
        return movies;
    }

    @Override
    public List<Movie.Episode> getEpisodes(String baseUrl, String playUrl) {
        // 实现获取剧集逻辑
        List<Movie.Episode> episodes = new ArrayList<>();
        
        // 示例数据
        for (int i = 1; i <= 10; i++) {
            Movie.Episode episode = new Movie.Episode();
            episode.setTitle("第" + i + "集");
            episode.setEpisodeUrl(baseUrl + "/episode/" + i);
            episodes.add(episode);
        }
        
        return episodes;
    }

    @Override
    public String getM3u8Url(String baseUrl, String episodeUrl) {
        // 实现获取m3u8播放地址逻辑
        return baseUrl + "/m3u8/" + episodeUrl.substring(episodeUrl.lastIndexOf('/') + 1) + ".m3u8";
    }
    
    @Override
    public MovieService getMovieServiceByDatasource(String datasourceId) {
        return this;
    }
}
```

## Maven构建配置 (可选)

如果您选择使用Maven构建，可以创建以下 `pom.xml` 文件：

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.yourcompany</groupId>
    <artifactId>custom-movie-datasource</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- 主项目依赖 -->
        <dependency>
            <groupId>org.example</groupId>
            <artifactId>get_movie_data</artifactId>
            <version>0.0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.2.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 配置文件

### movie-data-config.xml

在主项目的 `src/main/resources/config/movie-data-config.xml` 文件中添加您的数据源配置：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 数据源配置 -->
    <datasources>
        <!-- 您的自定义数据源配置 -->
        <datasource id="your-datasource-id" class="com.yourcompany.movies.YourCustomMovieService">
            <name>您的数据源名称</name>
            <description>您的数据源描述</description>
        </datasource>
    </datasources>
    
    <!-- URL映射配置 -->
    <urlMappings>
        <!-- 您的URL映射配置 -->
        <urlMapping baseUrl="https://your-target-website.com" datasource="your-datasource-id"/>
    </urlMappings>
</configuration>
```

## 构建和部署

### 使用Maven构建（如果使用Maven）

```bash
mvn clean package
```

### 使用Gradle构建（如果使用Gradle）

```bash
gradle build
```

### 手动编译和打包（不使用构建工具）

1. 编译Java源代码：
   ```bash
   javac -cp "path/to/get_movie_data-0.0.1-SNAPSHOT.jar" -d target src/main/java/com/yourcompany/movies/*.java
   ```

2. 创建JAR文件：
   ```bash
   jar cvf custom-movie-datasource.jar -C target .
   ```

### 部署

将生成的JAR文件复制到主项目的 `libs` 目录中：
```bash
cp custom-movie-datasource.jar /path/to/get_movie_data/libs/
```

## 使用方法

构建和部署完成后，您可以通过API调用测试您的自定义数据源：
```
GET /movies/search?baseUrl=https://your-target-website.com&keyword=搜索关键词&datasource=your-datasource-id
```