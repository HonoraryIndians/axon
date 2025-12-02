package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record CampaignSummary(
        Long campaignId,
        String campaignName,
        BigDecimal gmv,
        Long totalVisits,
        Double roas
) {}
