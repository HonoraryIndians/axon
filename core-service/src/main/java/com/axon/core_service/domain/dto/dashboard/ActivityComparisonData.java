package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record ActivityComparisonData(
        Long activityId,
        String activityName,
        String type,
        Long totalVisits,
        Long totalEngages,
        Double ctr,
        Long totalPurchases,
        BigDecimal gmv,
        Double conversionRate
) {}