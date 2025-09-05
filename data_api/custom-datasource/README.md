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