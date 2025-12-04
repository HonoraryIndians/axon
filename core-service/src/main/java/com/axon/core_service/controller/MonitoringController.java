package com.axon.core_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring/metrics")
@RequiredArgsConstructor
public class MonitoringController {

    private final RestClient.Builder restClientBuilder;

    @Value("${axon.entry-service-url}")
    private String entryServiceUrl;

    @GetMapping("/{serviceName}/{metricName}")
    public ResponseEntity<?> getMetric(
            @PathVariable String serviceName,
            @PathVariable String metricName,
            @RequestParam(required = false) String tag) {

        String baseUrl;
        if ("core".equalsIgnoreCase(serviceName)) {
            baseUrl = "http://localhost:8080"; // Self reference
        } else if ("entry".equalsIgnoreCase(serviceName)) {
            baseUrl = entryServiceUrl;
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown service: " + serviceName));
        }

        try {
            String url = baseUrl + "/actuator/metrics/" + metricName;
            if (tag != null) {
                url += "?tag=" + tag;
            }

            // Use a fresh RestClient to avoid base URL conflicts if configured globally
            Map response = restClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch metrics from {}: {}", serviceName, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch metrics", "details", e.getMessage()));
        }
    }
}
