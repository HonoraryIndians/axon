package com.axon.core_service.domain.dashboard;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "cohort_ltv_monthly_stats")
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class LTVBatch extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_activity_id")
    private CampaignActivity campaignActivity;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 월 식별 정보
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 캠페인 시작 후 경과 월 (0~11)
     * - 0: 캠페인 시작일 ~ +1개월
     * - 1: +1개월 ~ +2개월
     * - 11: +11개월 ~ +12개월
     */
    @Column(name = "month_offset", nullable = false)
    private Integer monthOffset;

    /**
     * 배치 작업 실행 시점 (매월 1일 새벽 3시)
     */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 코호트 기본 정보 (고정값, 참조용)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 코호트 시작일 (캠페인 시작일, 고정)
     */
    @Column(name = "cohort_start_date", nullable = false)
    private LocalDateTime cohortStartDate;

    /**
     * 신규 유입 고객 수 (첫 구매 고객)
     */
    @Column(name = "cohort_size", nullable = false)
    private Integer cohortSize;

    /**
     * 평균 CAC (고객 획득 비용)
     */
    @Column(name = "avg_cac", precision = 10, scale = 2, nullable = false)
    private BigDecimal avgCac;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 누적 지표 (해당 월까지 합산)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 누적 LTV (캠페인 시작 ~ 현재 월까지 총 매출)
     */
    @Column(name = "ltv_cumulative", precision = 12, scale = 2, nullable = false)
    private BigDecimal ltvCumulative;

    /**
     * LTV/CAC 비율 (누적)
     */
    @Column(name = "ltv_cac_ratio", precision = 5, scale = 2, nullable = false)
    private BigDecimal ltvCacRatio;

    /**
     * 누적 이익 (ltv_cumulative - 캠페인 예산)
     */
    @Column(name = "cumulative_profit", precision = 12, scale = 2, nullable = false)
    private BigDecimal cumulativeProfit;

    /**
     * 손익분기점 도달 여부
     */
    @Column(name = "is_break_even", nullable = false)
    private Boolean isBreakEven;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 월별 증분 지표 (해당 월에만 발생한 데이터)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 해당 월 매출
     */
    @Column(name = "monthly_revenue", precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlyRevenue;

    /**
     * 해당 월 주문 수
     */
    @Column(name = "monthly_orders", nullable = false)
    private Integer monthlyOrders;

    /**
     * 해당 월 활성 구매 고객 수
     */
    @Column(name = "active_users", nullable = false)
    private Integer activeUsers;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 고객 행동 지표 (누적)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 재구매율 (%)
     */
    @Column(name = "repeat_purchase_rate", precision = 5, scale = 2)
    private BigDecimal repeatPurchaseRate;

    /**
     * 평균 구매 횟수
     */
    @Column(name = "avg_purchase_frequency", precision = 5, scale = 2)
    private BigDecimal avgPurchaseFrequency;

    /**
     * 평균 주문 금액
     */
    @Column(name = "avg_order_value", precision = 10, scale = 2)
    private BigDecimal avgOrderValue;

    @Builder
    public LTVBatch(
            CampaignActivity campaignActivity,
            Integer monthOffset,
            LocalDateTime collectedAt,
            LocalDateTime cohortStartDate,
            Integer cohortSize,
            BigDecimal avgCac,
            BigDecimal ltvCumulative,
            BigDecimal ltvCacRatio,
            BigDecimal cumulativeProfit,
            Boolean isBreakEven,
            BigDecimal monthlyRevenue,
            Integer monthlyOrders,
            Integer activeUsers,
            BigDecimal repeatPurchaseRate,
            BigDecimal avgPurchaseFrequency,
            BigDecimal avgOrderValue
    ) {
        this.campaignActivity = campaignActivity;
        this.monthOffset = monthOffset;
        this.collectedAt = collectedAt;
        this.cohortStartDate = cohortStartDate;
        this.cohortSize = cohortSize;
        this.avgCac = avgCac;
        this.ltvCumulative = ltvCumulative;
        this.ltvCacRatio = ltvCacRatio;
        this.cumulativeProfit = cumulativeProfit;
        this.isBreakEven = isBreakEven != null ? isBreakEven : false;
        this.monthlyRevenue = monthlyRevenue;
        this.monthlyOrders = monthlyOrders;
        this.activeUsers = activeUsers;
        this.repeatPurchaseRate = repeatPurchaseRate;
        this.avgPurchaseFrequency = avgPurchaseFrequency;
        this.avgOrderValue = avgOrderValue;
    }
}
