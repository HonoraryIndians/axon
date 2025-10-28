package com.axon.core_service.support.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiErrorResponse {

    private final String error;
    private final String message;
    private final Long campaignActivityId;
}
