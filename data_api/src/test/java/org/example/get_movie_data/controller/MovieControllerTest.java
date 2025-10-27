package org.example.get_movie_data.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
public class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MovieRequest testRequest;

    @BeforeEach
    public void setUp() {
        testRequest = new MovieRequest();
        testRequest.setKeyword("测试");
    }

    @Test
    public void testSearchMoviesFromAllSources() throws Exception {
        String requestBody = objectMapper.writeValueAsString(testRequest);

        mockMvc.perform(post("/api/movie/search/all")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void testGetEpisodes() throws Exception {
        MovieRequest request = new MovieRequest();
        request.setBaseUrl("https://127.0.0.1/test");
        request.setPlayUrl("https://127.0.0.1/test/movie/1");

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/movie/episodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void testGetM3u8Url() throws Exception {
        MovieRequest request = new MovieRequest();
        request.setBaseUrl("https://127.0.0.1/test");
        request.setEpisodeUrl("https://127.0.0.1/test/episode/1");

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/movie/m3u8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}