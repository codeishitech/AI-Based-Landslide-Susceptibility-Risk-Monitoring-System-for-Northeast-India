package com.ner.landslide.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient beans for outbound integrations (ML risk engine, weather API,
 * satellite feed). Each has its own bean so timeouts/base URLs can differ
 * per downstream dependency.
 */
@Configuration
public class WebClientConfig {

    @Value("${app.ml-service.url}")
    private String mlServiceUrl;

    @Value("${app.weather-service.url}")
    private String weatherServiceUrl;

    @Value("${app.satellite-service.url:}")
    private String satelliteServiceUrl;

    private WebClient.Builder baseBuilder(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.create(ConnectionProvider.create("pool-" + baseUrl.hashCode(), 20))
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public WebClient mlRiskWebClient() {
        return baseBuilder(mlServiceUrl, 8000).build();
    }

    @Bean
    public WebClient weatherWebClient() {
        return baseBuilder(weatherServiceUrl, 5000).build();
    }

    @Bean
    public WebClient satelliteWebClient() {
        String url = (satelliteServiceUrl == null || satelliteServiceUrl.isBlank())
                ? weatherServiceUrl // fall back to a harmless default when unset in dev
                : satelliteServiceUrl;
        return baseBuilder(url, 10000).build();
    }
}
