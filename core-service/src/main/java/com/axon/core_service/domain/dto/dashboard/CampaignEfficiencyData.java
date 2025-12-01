package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record CampaignEfficiencyData(
        Long campaignId,
        String campaignName,
        BigDecimal budget, // X-axis
        BigDecimal gmv,    // Y-axis
        Double roas        // Tooltip or Color intensity
) {}
