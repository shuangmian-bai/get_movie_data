package org.example.get_movie_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

/**
 * 电影数据获取服务主应用类
 *
 * 这是一个基于Spring Boot的可扩展电影数据获取服务，
 * 支持通过外部JAR插件扩展数据源，无需修改主项目代码即可添加新的数据源实现。
 *
 * @author get_movie_data team
 * @version 1.0.0
 */

@SpringBootApplication
public class GetMovieDataApplication {

	public static void main(String[] args) {
		// 初始化基本文件和目录
		initializeBasicFiles();
		
		SpringApplication.run(GetMovieDataApplication.class, args);
	}
	
	/**
	 * 应用程序入口点
	 *
	 * @param args 命令行参数
	 */

	/**
	 * 初始化基本文件和目录
	 * 在首次运行时创建lib目录、movie-data-config.xml和使用说明
	 */
	private static void initializeBasicFiles() {
		try {
			// 获取当前jar文件路径
			URI jarUri = GetMovieDataApplication.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI();
			
			// 获取jar文件所在目录
			Path jarDir = Paths.get(jarUri).getParent();
			
			// 检查jarDir是否为null
			if (jarDir == null) {
				// 如果无法获取jar目录，则使用当前工作目录
				jarDir = Paths.get(".").toAbsolutePath().normalize();
			}
			
			// 创建lib目录
			Path libDir = jarDir.resolve("libs");
			if (!Files.exists(libDir)) {
				Files.createDirectories(libDir);
			}
			
			// 创建movie-data-config.xml文件
			Path configFile = jarDir.resolve("config/movie-data-config.xml");
			if (!Files.exists(configFile)) {
				// 从jar包中提取配置文件
				extractResourceFromJar(jarUri, "config/movie-data-config.xml", configFile);
			} else {
				// 检查配置文件是否包含自定义数据源配置，如果没有则添加
				String configContent = Files.readString(configFile);
				if (!configContent.contains("custom")) {
					// 备份原文件
					Files.move(configFile, jarDir.resolve("movie-data-config.xml.bak"), 
					           StandardCopyOption.REPLACE_EXISTING);
					// 从jar包中提取配置文件
					extractResourceFromJar(jarUri, "config/movie-data-config.xml", configFile);
				}
			}
			
			// 创建使用说明文件
			Path readmeFile = jarDir.resolve("README.md");
			if (!Files.exists(readmeFile)) {
				createReadmeFile(readmeFile);
			}
		} catch (Exception e) {
			System.err.println("初始化基本文件时出错: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * 从jar包中提取资源文件
	 */
	private static void extractResourceFromJar(URI jarUri, String resourcePath, Path targetPath) throws IOException {
		try {
			// 处理不同协议的URI
			String scheme = jarUri.getScheme();
			if ("file".equals(scheme)) {
				Path jarPath = Paths.get(jarUri);
				
				// 检查是否为目录（IDE运行情况）
				if (Files.isDirectory(jarPath)) {
					// 从classes目录中查找资源文件
					Path resourceFile = jarPath.resolve(resourcePath);
					if (Files.exists(resourceFile)) {
						// 如果资源文件存在，复制它
						Files.copy(resourceFile, targetPath);
					} else {
						// 如果资源文件不存在，创建基本配置文件
						createBasicConfigFile(targetPath);
					}
					return;
				}
				
				// 直接从文件系统中读取jar文件
				try (JarFile jarFile = new JarFile(jarPath.toFile())) {
					JarEntry entry = jarFile.getJarEntry(resourcePath);
					if (entry != null) {
						try (InputStream inputStream = jarFile.getInputStream(entry);
							 OutputStream outputStream = Files.newOutputStream(targetPath)) {
							byte[] buffer = new byte[8192];
							int bytesRead;
							while ((bytesRead = inputStream.read(buffer)) != -1) {
								outputStream.write(buffer, 0, bytesRead);
							}
						}
					} else {
						// 如果在jar包中找不到配置文件，则创建一个基本的配置文件
						createBasicConfigFile(targetPath);
					}
				}
			} else if ("jar".equals(scheme)) {
				// 从jar协议的URI中提取文件路径
				String jarPath = jarUri.toString();
				// jar:file:/path/to/file.jar!/...
				int separatorIndex = jarPath.indexOf("!/");
				if (separatorIndex != -1) {
					String filePath = jarPath.substring(4, separatorIndex); // 移除 "jar:" 和 "!/"
					if (filePath.startsWith("file:")) {
						filePath = filePath.substring(5); // 移除 "file:"
					}
					
					// 处理Windows路径前缀
					if (filePath.startsWith("/")) {
						// 检查是否为Windows路径如 /C:/...
						if (filePath.length() > 3 && filePath.charAt(2) == ':') {
							filePath = filePath.substring(1);
						}
					}
					
					try (JarFile jarFile = new JarFile(filePath)) {
						JarEntry entry = jarFile.getJarEntry(resourcePath);
						if (entry != null) {
							try (InputStream inputStream = jarFile.getInputStream(entry);
								 OutputStream outputStream = Files.newOutputStream(targetPath)) {
								byte[] buffer = new byte[8192];
								int bytesRead;
								while ((bytesRead = inputStream.read(buffer)) != -1) {
									outputStream.write(buffer, 0, bytesRead);
								}
							}
						} else {
							// 如果在jar包中找不到配置文件，则创建一个基本的配置文件
							createBasicConfigFile(targetPath);
						}
					}
				} else {
					// 如果无法解析jar路径，则创建一个基本的配置文件
					createBasicConfigFile(targetPath);
				}
			} else {
				// 对于其他协议，尝试直接从jar包中读取
				try (JarFile jarFile = new JarFile(Paths.get(jarUri).toFile())) {
					JarEntry entry = jarFile.getJarEntry(resourcePath);
					if (entry != null) {
						try (InputStream inputStream = jarFile.getInputStream(entry);
							 OutputStream outputStream = Files.newOutputStream(targetPath)) {
							byte[] buffer = new byte[8192];
							int bytesRead;
							while ((bytesRead = inputStream.read(buffer)) != -1) {
								outputStream.write(buffer, 0, bytesRead);
							}
						}
					} else {
						// 如果在jar包中找不到配置文件，则创建一个基本的配置文件
						createBasicConfigFile(targetPath);
					}
				} catch (Exception e) {
					// 如果出现异常，则创建一个基本的配置文件
					createBasicConfigFile(targetPath);
				}
			}
		} catch (Exception e) {
			// 如果出现任何异常，则创建一个基本的配置文件
			createBasicConfigFile(targetPath);
		}
	}
	
	/**
	 * 创建基本的配置文件
	 */
	private static void createBasicConfigFile(Path targetPath) throws IOException {
		String basicConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<!-- 电影数据源配置文件 -->\n" +
				"<configuration>\n" +
				"    <!-- 数据源配置部分 -->\n" +
				"    <datasources>\n" +
				"        <!-- 测试数据源，用于演示和默认情况 -->\n" +
				"        <datasource id=\"test_demo\" class=\"org.example.get_movie_data.service.DataSourceManager$DefaultMovieService\">\n" +
				"            <name>测试数据源</name>\n" +
				"            <description>测试数据源</description>\n" +
				"        </datasource>\n" +
				"    </datasources>\n" +
				"\n" +
				"    <!-- URL映射配置部分 -->\n" +
				"    <urlMappings>\n" +
				"        <!-- 通配符匹配，作为默认数据源 -->\n" +
				"        <urlMapping baseUrl=\"*\" datasource=\"test_demo\"/>\n" +
				"    </urlMappings>\n" +
				"</configuration>";
		
		Files.write(targetPath, basicConfig.getBytes());
	}
	
	/**
	 * 创建README.md文件
	 */
	private static void createReadmeFile(Path targetPath) throws IOException {
		String readmeContent = "# 电影数据获取系统\n\n" +
				"这是一个用于获取电影数据的系统，支持通过不同的数据源获取电影信息。\n\n" +
				"## 快速开始\n\n" +
				"1. 确保已经安装了Java 17或更高版本\n" +
				"2. 运行此jar包: `java -jar get_movie_data-0.0.1-SNAPSHOT.jar`\n" +
				"3. 系统将自动创建以下文件和目录：\n" +
				"   - `libs/` 目录：用于存放扩展的数据源实现jar包\n" +
				"   - `cache/` 目录：用于存放缓存文件\n" +
				"   - `movie-data-config.xml` 文件：数据源和URL映射配置文件\n" +
				"   - `README.md` 文件：使用说明\n\n" +
				"## API接口\n\n" +
				"系统提供以下RESTful API接口：\n\n" +
				"### 搜索电影\n\n" +
				"```\n" +
				"GET /api/movie/search?keyword={keyword}\n" +
				"```\n\n" +
				"参数说明：\n" +
				"- keyword: 搜索关键词\n\n" +
				"### 搜索特定数据源的电影\n\n" +
				"```\n" +
				"GET /api/movie/search?baseUrl={baseUrl}&keyword={keyword}\n" +
				"```\n\n" +
				"参数说明：\n" +
				"- baseUrl: 基础URL，用于匹配特定数据源\n" +
				"- keyword: 搜索关键词\n\n" +
				"### 获取所有数据源的电影\n\n" +
				"```\n" +
				"GET /api/movie/searchAll?keyword={keyword}\n" +
				"```\n\n" +
				"参数说明：\n" +
				"- keyword: 搜索关键词\n\n" +
				"### 获取剧集列表\n\n" +
				"```\n" +
				"GET /api/movie/episodes?baseUrl={baseUrl}&playUrl={playUrl}\n" +
				"```\n\n" +
				"参数说明：\n" +
				"- baseUrl: 基础URL\n" +
				"- playUrl: 播放地址\n\n" +
				"### 获取M3U8地址\n\n" +
				"```\n" +
				"GET /api/movie/m3u8?baseUrl={baseUrl}&episodeUrl={episodeUrl}\n" +
				"```\n\n" +
				"参数说明：\n" +
				"- baseUrl: 基础URL\n" +
				"- episodeUrl: 剧集播放地址\n\n" +
				"## 缓存机制\n\n" +
				"系统内置缓存机制，避免重复爬取相同数据，提高响应速度：\n\n" +
				"- 所有API接口调用结果都会被缓存\n" +
				"- 缓存默认保存在 `cache/` 目录下\n" +
				"- 缓存默认有效期为2小时\n" +
				"- 缓存过期后会自动重新爬取数据\n\n" +
				"### 缓存配置\n\n" +
				"缓存时间在 `org.example.get_movie_data.service.CacheManager` 类中配置：\n\n" +
				"```java\n" +
				"// 缓存过期时间（毫秒）- 默认2小时\n" +
				"private static final long CACHE_EXPIRE_TIME = 2 * 60 * 60 * 1000;\n" +
				"```\n\n" +
				"如需修改缓存时间，可以修改该常量值并重新构建项目。\n\n" +
				"## 配置说明\n\n" +
				"系统通过 `movie-data-config.xml` 文件进行配置，包括数据源配置和URL映射配置。\n\n" +
				"### 数据源配置\n\n" +
				"```xml\n" +
				"<datasources>\n" +
				"    <datasource id=\"test_demo\" class=\"org.example.get_movie_data.service.DataSourceManager$DefaultMovieService\">\n" +
				"        <name>测试数据源</name>\n" +
				"        <description>测试数据源</description>\n" +
				"    </datasource>\n" +
				"</datasources>\n" +
				"```\n\n" +
				"### URL映射配置\n\n" +
				"```xml\n" +
				"<urlMappings>\n" +
				"    <urlMapping baseUrl=\"*\" datasource=\"test_demo\"/>\n" +
				"</urlMappings>\n" +
				"```\n\n" +
				"## 扩展数据源\n\n" +
				"要扩展新的数据源，请按照以下步骤操作：\n\n" +
				"1. 创建实现ExternalMovieService接口的类\n" +
				"2. 将实现类打包为jar文件\n" +
				"3. 将jar文件放入 `libs` 目录\n" +
				"4. 在 `movie-data-config.xml` 文件中添加数据源配置\n" +
				"5. （可选）添加URL映射配置\n\n" +
				"示例配置：\n\n" +
				"```xml\n" +
				"<datasources>\n" +
				"    <datasource id=\"chabeigu\" class=\"com.example.chabeigu.ChabeiguMovieService\">\n" +
				"        <name>茶杯狐数据源</name>\n" +
				"        <description>从茶杯狐网站获取电影数据</description>\n" +
				"    </datasource>\n" +
				"</datasources>\n\n" +
				"<urlMappings>\n" +
				"    <urlMapping baseUrl=\"https://www.chabeigu.com\" datasource=\"chabeigu\"/>\n" +
				"</urlMappings>\n" +
				"```\n\n" +
				"## 项目结构\n\n" +
				"```\n" +
				"get_movie_data/\n" +
				"├── libs/                          # 外部数据源JAR包存放目录\n" +
				"├── cache/                         # 缓存文件存放目录\n" +
				"├── movie-data-config.xml          # 数据源配置文件\n" +
				"├── README.md                      # 使用说明文件\n" +
				"├── chabeigu/                      # 茶杯狐数据源示例项目\n" +
				"└── custom-datasource/             # 自定义数据源示例项目\n" +
				"```\n\n" +
				"## 内置数据源\n\n" +
				"系统内置以下数据源：\n\n" +
				"- **测试数据源(test_demo)**: 用于演示和默认情况的测试数据源\n" +
				"- **茶杯狐数据源(chabeigu)**: 从茶杯狐网站获取电影数据的示例实现\n\n" +
				"详细使用说明请参考项目目录中的README.md文件。";
		
		Files.write(targetPath, readmeContent.getBytes());
	}
}