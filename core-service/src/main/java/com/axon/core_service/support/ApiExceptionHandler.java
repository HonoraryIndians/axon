package com.axon.core_service.support;

import com.axon.core_service.exception.CampaignActivityNotFoundException;
import com.axon.core_service.support.response.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CampaignActivityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCampaignActivityNotFound(CampaignActivityNotFoundException ex) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .error("CAMPAIGN_ACTIVITY_NOT_FOUND")
                .message(ex.getReason())
                .campaignActivityId(ex.getCampaignActivityId())
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(body);

    }
}
