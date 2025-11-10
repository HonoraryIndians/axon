package com.axon.entry_service.service;

import com.axon.messaging.dto.validation.ValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoreValidationService {
    private final WebClient webClient;

    /**
     * Determine eligibility by calling the core validation API for a campaign activity.
     *
     * @param Token               the Authorization header value (including scheme, e.g. "Bearer ...")
     * @param campaignActivityId  the ID of the campaign activity to validate
     * @return                    a ValidationResponse containing the eligibility result; if the external call fails, `eligible` will be `false` and `errorMessage` will be "서버 오류"
     */
    public ValidationResponse isEligible(String Token, Long campaignActivityId) {
        try {
            ValidationResponse response = webClient.get()
                    .uri(uriBuilder ->uriBuilder
                            .path("/api/v1/validation")
                            .queryParam("campaignActivityId", campaignActivityId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION,Token)
                    .retrieve()
                    .bodyToMono(ValidationResponse.class)
                    .block();
            log.info("SERVICE - return {} || {} ", response.isEligible(),response);
            return response;
        } catch (Exception e) {
            log.error("CoreService로의 검증 로직에 오류가 발생 ", e);
            return ValidationResponse.builder().eligible(false).errorMessage("서버 오류").build();
        }
    }
}