package com.axon.entry_service.domain;

import com.axon.messaging.CampaignActivityType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CampaignActivityMeta(
        Long id,
        Long campaignId,
        Integer limitCount,
        CampaignActivityStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<Map<String, Object>> filters,
        boolean hasFastValidation,
        boolean hasHeavyValidation,
        Long productId,
        CampaignActivityType campaignActivityType
) {

    public CampaignActivityMeta {
        Objects.requireNonNull(id, "campaignActivityId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Determines whether the campaign activity is eligible for participation at the given instant.
     *
     * @param now the reference instant used to evaluate eligibility (converted to the system default time zone)
     * @return true if the activity's limit is greater than zero or unset, the status is active, the reference time is not before `startDate` (if set), and not after `endDate` (if set); false otherwise
     */
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