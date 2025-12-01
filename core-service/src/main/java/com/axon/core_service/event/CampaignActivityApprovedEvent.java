package com.axon.core_service.event;

import java.time.Instant;

public record CampaignActivityApprovedEvent(
        Long campaignId,
        Long campaignActivityId,
        Long userId,
        Long productId,
        Instant occurredAt
) {
}
