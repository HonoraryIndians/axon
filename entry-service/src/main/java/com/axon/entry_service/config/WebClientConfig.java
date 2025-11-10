package com.axon.entry_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Create a WebClient configured for HTTP calls to the campaign/core service.
     *
     * @param baseUrl the base URL of the core service (sourced from `axon.core-service.base-url`, defaults to `http://localhost:8080`)
     * @return a WebClient instance with its base URL set to the provided `baseUrl`
     */
    @Bean
    public WebClient campaignWebClient(
            @Value("${axon.core-service.base-url:http://localhost:8080}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}