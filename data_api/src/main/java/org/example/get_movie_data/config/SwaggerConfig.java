package org.example.get_movie_data.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电影数据API接口文档")
                        .version("1.0")
                        .description("提供电影搜索、剧集信息和播放地址获取等功能的API接口")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@example.com")));
    }
}