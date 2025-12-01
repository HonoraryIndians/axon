package com.axon.entry_service.dto;

import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.messaging.CampaignActivityType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignActivitySummaryResponse(
        Long id,
        Long campaignId,
        Integer limitCount,
        CampaignActivityStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<Map<String,Object>> filters,
        Long productId,
        CampaignActivityType activityType
) {}
