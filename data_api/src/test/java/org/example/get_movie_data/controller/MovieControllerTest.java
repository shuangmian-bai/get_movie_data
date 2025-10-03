package org.example.get_movie_data.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.logging.Logger;
import java.util.logging.Level;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
public class MovieControllerTest {

    private static final Logger logger = Logger.getLogger(MovieControllerTest.class.getName());

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSearchMoviesFromAllSources() throws Exception {
        // 测试搜索电影接口
        ResultActions result = mockMvc.perform(get("/api/movie/search/all")
                .param("keyword", "test"))
                .andDo(print())
                .andExpect(status().isOk());
        
        // 记录响应状态和数据大小
        String content = result.andReturn().getResponse().getContentAsString();
        int statusCode = result.andReturn().getResponse().getStatus();
        logger.info("Search API - Status Code: " + statusCode + ", Response Size: " + content.length() + 
                   ", Sample Content: " + (content.length() > 100 ? content.substring(0, 100) : content));
    }

    @Test
    public void testGetEpisodes() throws Exception {
        // 测试获取剧集信息接口
        ResultActions result = mockMvc.perform(get("/api/movie/episodes")
                .param("baseUrl", "http://example.com")
                .param("playUrl", "http://example.com/play/123"))
                .andDo(print())
                .andExpect(status().isOk());
        
        // 记录响应状态和数据大小
        String content = result.andReturn().getResponse().getContentAsString();
        int statusCode = result.andReturn().getResponse().getStatus();
        logger.info("Episodes API - Status Code: " + statusCode + ", Response Size: " + content.length() + 
                   ", Sample Content: " + (content.length() > 100 ? content.substring(0, 100) : content));
    }

    @Test
    public void testGetM3u8Url() throws Exception {
        // 测试获取m3u8地址接口
        ResultActions result = mockMvc.perform(get("/api/movie/m3u8")
                .param("baseUrl", "http://example.com")
                .param("episodeUrl", "http://example.com/episode/123"))
                .andDo(print())
                .andExpect(status().isOk());
        
        // 记录响应状态和数据大小
        String content = result.andReturn().getResponse().getContentAsString();
        int statusCode = result.andReturn().getResponse().getStatus();
        logger.info("M3U8 API - Status Code: " + statusCode + ", Response Size: " + content.length() + 
                   ", Sample Content: " + (content.length() > 100 ? content.substring(0, 100) : content));
    }
}