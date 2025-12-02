package com.axon.core_service.domain.dto.dashboard;

public record CampaignRankData(
    Long campaignId,
    String campaignName,
    Long value,
    String formattedValue
) {}