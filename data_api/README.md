# 电影数据获取服务

这是一个可扩展的电影数据获取服务，支持通过外部JAR插件扩展数据源。

## 打包和部署说明

### 打包命令
```bash
mvn clean package
```

执行该命令将在 `target` 目录下生成以下内容：
- `get_movie_data-0.0.1-SNAPSHOT.jar` - 主程序jar文件
- `config/` - 配置文件目录
- `libs/` - 外部数据源插件目录

### 目录结构
```
application/
├── get_movie_data-0.0.1-SNAPSHOT.jar
├── config/
│   └── movie-data-config.xml
└── libs/
    ├── bfzy.jar
    ├── chabeigu.jar
    └── yuny.jar
```

### 运行应用
```bash
java -jar get_movie_data-0.0.1-SNAPSHOT.jar
```

## 配置说明

### 修改数据源配置
要添加、删除或修改数据源，请编辑 `config/movie-data-config.xml` 文件。

配置文件结构如下：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- 电影数据源配置文件 -->
<configuration>
    <!-- 数据源配置部分 -->
    <datasources>
        <!-- 示例数据源 -->
        <datasource id="数据源ID" class="完整类名">
            <name>数据源名称</name>
            <description>数据源描述</description>
        </datasource>
    </datasources>

    <!-- URL映射配置部分 -->
    <urlMappings>
        <!-- URL到数据源的映射关系 -->
        <urlMapping baseUrl="数据源基础URL" datasource="数据源ID"/>

        <!-- 通配符匹配，作为默认数据源 -->
        <urlMapping baseUrl="*" datasource="默认数据源ID"/>
    </urlMappings>
</configuration>
```

### 添加新的数据源插件
1. 开发符合接口规范的数据源插件JAR包
2. 将JAR包放入 `libs/` 目录
3. 在 `config/movie-data-config.xml` 中添加相应的数据源配置
4. 重启应用程序使配置生效

## 接口使用说明

启动应用后，默认会在8080端口提供HTTP服务：

- `GET /movies?url=[目标网址]` - 根据提供的URL获取电影数据

例如：`curl http://localhost:8080/movies?url=https://bfzy.tv/sample`

## 常见问题

### Q: 如何修改配置文件？
A: 配置文件位于应用目录下的 `config/movie-data-config.xml`，可以直接编辑该文件并重启应用。

### Q: 如何添加自定义数据源？
A: 开发符合规范的JAR包，放到 `libs/` 目录下，并在配置文件中添加相应配置项。

### Q: 配置未生效怎么办？
A: 检查配置文件格式是否正确，确认配置文件位于正确的目录位置，然后重启应用程序.
