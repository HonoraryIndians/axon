package com.axon.entry_service.domain;

public enum CampaignActivityStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED;

    /**
     * Indicates whether this status represents an active campaign.
     *
     * @return true if the status is ACTIVE, false otherwise.
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
}