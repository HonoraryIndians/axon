package com.axon.core_service.domain.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cohort 분석 응답 DTO
 * 특정 시점에 획득한 고객 그룹의 장기 가치 분석
 */
public record CohortAnalysisResponse(
        String cohortId,                // Cohort 식별자 (e.g., "2024-11-21" 또는 "activity-2")
        String cohortName,              // Cohort 이름 (e.g., "2024-11 블랙프라이데이")
        LocalDateTime cohortStartDate,  // Cohort 시작일
        LocalDateTime cohortEndDate,    // Cohort 종료일

        // 고객 획득 정보
        Long totalCustomers,            // 총 고객 수
        BigDecimal totalAcquisitionCost, // 총 획득 비용 (마케팅 예산)
        BigDecimal avgCAC,              // 평균 CAC (Customer Acquisition Cost)

        // 시간별 LTV 성장
        BigDecimal ltv30d,              // 30일 LTV
        BigDecimal ltv90d,              // 90일 LTV
        BigDecimal ltv365d,             // 365일 LTV
        BigDecimal ltvCurrent,          // 현재까지 LTV

        // 효율성 지표
        Double ltvCacRatio30d,          // LTV/CAC 비율 (30일)
        Double ltvCacRatio90d,          // LTV/CAC 비율 (90일)
        Double ltvCacRatio365d,         // LTV/CAC 비율 (365일)
        Double ltvCacRatioCurrent,      // LTV/CAC 비율 (현재)

        // 재구매 분석
        Double repeatPurchaseRate,      // 재구매율 (%)
        Double avgPurchaseFrequency,    // 평균 구매 횟수
        BigDecimal avgOrderValue,       // 평균 주문 금액

        // 메타 정보
        LocalDateTime calculatedAt      // 계산 시점
) {
    /**
     * 기본 생성자 (필수 필드만)
     */
    public CohortAnalysisResponse(
            String cohortId,
            String cohortName,
            LocalDateTime cohortStartDate,
            Long totalCustomers,
            BigDecimal totalAcquisitionCost
    ) {
        this(
                cohortId,
                cohortName,
                cohortStartDate,
                null, // cohortEndDate
                totalCustomers,
                totalAcquisitionCost,
                calculateAvgCAC(totalAcquisitionCost, totalCustomers),
                BigDecimal.ZERO, // ltv30d
                BigDecimal.ZERO, // ltv90d
                BigDecimal.ZERO, // ltv365d
                BigDecimal.ZERO, // ltvCurrent
                0.0, // ltvCacRatio30d
                0.0, // ltvCacRatio90d
                0.0, // ltvCacRatio365d
                0.0, // ltvCacRatioCurrent
                0.0, // repeatPurchaseRate
                0.0, // avgPurchaseFrequency
                BigDecimal.ZERO, // avgOrderValue
                LocalDateTime.now()
        );
    }

    /**
     * CAC 계산 헬퍼
     */
    private static BigDecimal calculateAvgCAC(BigDecimal totalCost, Long customers) {
        if (customers == null || customers == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(
                BigDecimal.valueOf(customers),
                2,
                java.math.RoundingMode.HALF_UP
        );
    }
}
