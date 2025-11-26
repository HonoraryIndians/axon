package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;

/**
 * Overview data for dashboard KPIs.
 * Uses universal funnel terminology across all activity types.
 * Includes marketing metrics: GMV, conversion rate, ROAS.
 */
public record OverviewData (
        // Funnel counts
        Long totalVisits,      // VISIT funnel step
        Long totalEngages,     // ENGAGE funnel step (CLICK/APPLY/CLAIM)
        Long totalQualifies,   // QUALIFY funnel step (APPROVED/WON/ISSUED)
        Long purchaseCount,    // PURCHASE funnel step

        // Marketing metrics
        BigDecimal gmv,                 // Gross Merchandise Value (총 거래액)
        Double conversionRate,          // Visit → Purchase (%)
        Double engagementRate,          // Visit → Engage (%)
        Double qualificationRate,       // Engage → Qualify (%)
        Double purchaseRate,            // Qualify → Purchase (%)
        BigDecimal averageOrderValue,   // GMV / Purchase count (평균 객단가)
        BigDecimal budget,              // Campaign activity budget (예산)
        Double roas                     // Return on Ad Spend (%) - (GMV/Budget)*100
){
    /**
     * Calculate conversion rate if not provided.
     */
    public Double getConversionRate() {
        if (conversionRate != null) return conversionRate;
        if (totalVisits == null || totalVisits == 0) return 0.0;
        return (purchaseCount * 100.0) / totalVisits;
    }
}
