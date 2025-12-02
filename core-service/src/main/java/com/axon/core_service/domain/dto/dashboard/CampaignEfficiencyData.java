package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record CampaignEfficiencyData(
    Long campaignId,
    String campaignName,
    BigDecimal budget,
    BigDecimal gmv,
    Double roas
) {}