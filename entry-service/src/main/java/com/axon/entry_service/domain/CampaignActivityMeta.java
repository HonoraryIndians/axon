package com.axon.entry_service.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

public record CampaignActivityMeta(
        Long id,
        Integer limitCount,
        CampaignActivityStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate
) {

    public CampaignActivityMeta {
        Objects.requireNonNull(id, "campaignActivityId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isParticipatable(Instant now) {
        if (limitCount != null && limitCount <= 0) {
            return false;
        }
        if (!status.isActive()) {
            return false;
        }

        LocalDateTime current = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        if (startDate != null && current.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !current.isAfter(endDate);
    }
}
