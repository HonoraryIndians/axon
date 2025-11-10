package com.axon.core_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CampaignActivityNotFoundException extends ResponseStatusException {

    private final Long campaignActivityId;

    /**
     * Creates an exception indicating the campaign activity with the given ID was not found (HTTP 404).
     *
     * @param campaignActivityId the identifier of the campaign activity that was not found
     */
    public CampaignActivityNotFoundException(Long campaignActivityId) {
        super(HttpStatus.NOT_FOUND, "존재하지 않는 캠페인 활동입니다.");
        this.campaignActivityId = campaignActivityId;
    }

    /**
     * Gets the ID of the campaign activity associated with this exception.
     *
     * @return the campaign activity ID that caused the exception
     */
    public Long getCampaignActivityId() {
        return campaignActivityId;
    }
}