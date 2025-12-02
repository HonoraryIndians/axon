package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

public record ActivityComparisonData(
        Long activityId,
        String activityName,
        String type,
        String category, // Added
        Long totalVisits,
        Long totalEngages, // Added
        Long totalPurchases,
        BigDecimal gmv,
        Double engagementRate, // Added
        Double conversionRate
) {}