package com.axon.core_service.support;

import com.axon.core_service.exception.EventNotFoundException;
import com.axon.core_service.support.response.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEventNotFoundException(EventNotFoundException ex) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .error("EVENT_NOT_FOUND")
                .message(ex.getReason())
                .eventId(ex.getEventId())
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(body);

    }
}