package com.axon.core_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CampaignActivityNotFoundException extends ResponseStatusException {

    private final Long campaignActivityId;

    public CampaignActivityNotFoundException(Long campaignActivityId) {
        super(HttpStatus.NOT_FOUND, "존재하지 않는 캠페인 활동입니다.");
        this.campaignActivityId = campaignActivityId;
    }

    public Long getCampaignActivityId() {
        return campaignActivityId;
    }
}
