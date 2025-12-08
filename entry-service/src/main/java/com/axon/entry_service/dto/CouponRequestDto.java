package com.axon.entry_service.dto;

import com.axon.messaging.CampaignActivityType;

public record CouponRequestDto (
        Long userId,
        Long campaignActivityId,
        Long productId,
        CampaignActivityType campaignActivityType
)
{}
