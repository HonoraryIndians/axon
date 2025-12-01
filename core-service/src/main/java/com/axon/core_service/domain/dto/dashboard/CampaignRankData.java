package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record CampaignRankData(
        Long campaignId,
        String campaignName,
        Long value, // Visits or GMV
        String formattedValue // Display string (e.g. "1,234" or "₩1,000,000")
) {}
