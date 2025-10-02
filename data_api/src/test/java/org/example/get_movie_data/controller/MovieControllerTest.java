package org.example.get_movie_data.controller;

import org.example.get_movie_data.controller.MovieResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MovieControllerTest {

    private static final Logger logger = Logger.getLogger(MovieControllerTest.class.getName());

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        baseUrl = "http://localhost:" + port + "/api/movie";
    }

    @Test
    public void testSearchMoviesFromAllSources() {
        String keyword = "test";
        String url = baseUrl + "/search/all?keyword=" + keyword;

        logger.info("Testing search movies API: " + url);

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        
        logger.info("Search movies response status: " + response.getStatusCode());
        logger.info("Search movies response body size: " + (response.getBody() != null ? response.getBody().size() : 0));
        if (response.getBody() != null) {
            logger.info("First few items in search result: " + 
                (response.getBody().size() > 3 ? response.getBody().subList(0, 3) : response.getBody()));
        }

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    public void testGetEpisodes() {
        String testBaseUrl = "http://example.com";
        String playUrl = "http://example.com/play/123";
        String url = baseUrl + "/episodes?baseUrl=" + testBaseUrl + "&playUrl=" + playUrl;

        logger.info("Testing get episodes API: " + url);

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        
        logger.info("Get episodes response status: " + response.getStatusCode());
        logger.info("Get episodes response body size: " + (response.getBody() != null ? response.getBody().size() : 0));
        if (response.getBody() != null) {
            logger.info("First few episodes in result: " + 
                (response.getBody().size() > 3 ? response.getBody().subList(0, 3) : response.getBody()));
        }

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    public void testGetM3u8Url() {
        String testBaseUrl = "http://example.com";
        String episodeUrl = "http://example.com/episode/123";
        String url = baseUrl + "/m3u8?baseUrl=" + testBaseUrl + "&episodeUrl=" + episodeUrl;

        logger.info("Testing get m3u8 URL API: " + url);

        ResponseEntity<MovieResponse> response = restTemplate.getForEntity(url, MovieResponse.class);
        
        logger.info("Get m3u8 URL response status: " + response.getStatusCode());
        logger.info("Get m3u8 URL response body: " + response.getBody());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}