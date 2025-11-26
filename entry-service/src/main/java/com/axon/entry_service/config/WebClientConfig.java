package com.axon.entry_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    /**
     * Create a RestClient configured for HTTP calls to the campaign/core service.
     * Replaces WebClient for Virtual Threads compatibility.
     *
     * @param baseUrl the base URL of the core service (sourced from `axon.core-service.base-url`, defaults to `http://localhost:8080`)
     * @return a RestClient instance with its base URL set to the provided `baseUrl`
     */
    @Bean
    public RestClient campaignRestClient(
            @Value("${axon.core-service.base-url:http://localhost:8080}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
