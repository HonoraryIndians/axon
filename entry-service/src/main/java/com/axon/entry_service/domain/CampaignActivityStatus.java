package com.axon.entry_service.domain;

public enum CampaignActivityStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
