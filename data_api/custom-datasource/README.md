# 自定义数据源扩展包

这是一个为 `get_movie_data` 项目开发的外部数据源扩展包模板，可以作为开发其他数据源的参考。

## 项目结构

- `CustomMovieService.java`: 自定义数据源的主要实现类
- `Movie.java`: 电影数据模型类
- `MovieService.java`: 服务接口

## 使用方法

1. 将此项目作为模板创建新的数据源项目
2. 修改包名和类名避免冲突
3. 实现具体的业务逻辑
4. 将项目打包成 JAR 文件
5. 将生成的 JAR 文件放置在主项目的 `libs` 目录下
6. 在主项目的 `movie-data-config.xml` 配置文件中添加数据源配置

## 打包命令

```bash
mvn clean package
```

## 缓存机制

本项目作为 `get_movie_data` 的扩展数据源，会自动享受主项目提供的缓存功能：

- 所有API调用结果都会被缓存
- 缓存默认保存在主项目的 `cache/` 目录下
- 缓存默认有效期为2小时
- 缓存过期后会自动重新爬取数据

### 缓存配置

缓存时间在主项目中的 `org.example.get_movie_data.service.CacheManager` 类中配置：

```java
// 缓存过期时间（毫秒）- 默认2小时
private static final long CACHE_EXPIRE_TIME = 2 * 60 * 60 * 1000;
```

如需修改缓存时间，需要修改主项目的该常量值并重新构建主项目。