package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record ActivitySummary(
        Long activityId,
        String activityName,
        String category,
        BigDecimal gmv,
        Long totalVisits,
        Double conversionRate
) {}
