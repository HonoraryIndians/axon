package com.axon.core_service.support;

import com.axon.core_service.exception.CampaignActivityNotFoundException;
import com.axon.core_service.support.response.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Builds an ApiErrorResponse for a missing campaign activity and returns it with the exception's HTTP status.
     *
     * @param ex the thrown CampaignActivityNotFoundException containing the reason, campaignActivityId, and HTTP status
     * @return a ResponseEntity whose body is an ApiErrorResponse with error `"CAMPAIGN_ACTIVITY_NOT_FOUND"`, the exception's reason as the message, and the exception's campaignActivityId; the response status is taken from the exception
     */
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