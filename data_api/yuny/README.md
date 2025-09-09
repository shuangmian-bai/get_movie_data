# 云云TV数据源插件

这是一个用于从云云TV网站(https://www.yuny.tv)获取电影数据的数据源插件。

## 项目结构

```
yuny/
├── src/main/java/com/example/yuny/
│   ├── YunyMovieService.java    # 云云TV电影服务实现类
│   ├── Movie.java               # 电影数据模型类
│   └── MovieService.java        # 电影服务接口
├── pom.xml                      # Maven配置文件
└── README.md                    # 项目说明文档
```

## 功能说明

该插件实现了MovieService接口，提供以下功能：
1. 根据关键词搜索电影
2. 获取电影详细信息及剧集列表
3. 解析电影播放地址获取m3u8链接

## 构建项目

```bash
# 进入yuny目录
cd yuny

# 使用Maven构建项目
mvn clean package
```

构建完成后，会在target目录下生成jar包。

## 使用方法

1. 将生成的jar包复制到主项目的libs目录下
2. 在主项目的配置文件中添加数据源配置
3. 启动主项目即可使用该数据源