# 测试说明

## 测试结构

本项目包含以下几种类型的测试：

1. **控制器测试** - 测试主项目的API接口
2. **数据源测试** - 测试各个数据源实现的功能
3. **集成测试** - 测试整个系统的集成功能

## 如何运行测试

### 运行所有测试

```bash
./mvnw test
```

### 运行特定测试类

```bash
./mvnw test -Dtest=MovieControllerTest
./mvnw test -Dtest=ChabeiguMovieServiceTest
./mvnw test -Dtest=BfzyMovieServiceTest
./mvnw test -Dtest=YunyMovieServiceTest
```

### 使用注解运行特定数据源测试

每个数据源测试类都使用了`@DataSourceTest`注解，可以通过注解值来识别要测试的数据源：

- `@DataSourceTest("chabeigu")` - 茶杯狐数据源测试
- `@DataSourceTest("bfzy")` - 暴风影音数据源测试
- `@DataSourceTest("yuny")` - 云云TV数据源测试

## 测试内容

### 控制器测试 (MovieControllerTest)
- 测试 `/api/movie/search/all` 接口
- 测试 `/api/movie/episodes` 接口
- 测试 `/api/movie/m3u8` 接口

### 数据源测试
每个数据源测试类都测试以下功能：
- `searchMovies` - 搜索电影功能
- `getEpisodes` - 获取剧集列表功能
- `getM3u8Url` - 获取M3U8播放地址功能

## 测试输出

测试运行时会输出以下信息：
- 响应状态码
- 响应内容大小
- 部分响应内容（前500个字符）
- 搜索到的电影/剧集数量
- 电影/剧集的详细信息