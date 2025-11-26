package com.axon.core_service.service;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dashboard.FunnelStep;
import com.axon.core_service.domain.dto.dashboard.*;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.service.BehaviorEventService;
import com.axon.core_service.service.CampaignMetricsService;
import com.axon.core_service.service.RealtimeMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final RealtimeMetricsService realtimeMetricsService;
    private final BehaviorEventService behaviorEventService;
    private final com.axon.core_service.repository.CampaignRepository campaignRepository;
    private final com.axon.core_service.repository.CampaignActivityRepository campaignActivityRepository;

    public DashboardResponse getDashboardByActivity(Long activityId, DashboardPeriod period,
            LocalDateTime customStart, LocalDateTime customEnd) {
        LocalDateTime start = customStart != null ? customStart
                : period.getStartDateTime();
        LocalDateTime end = LocalDateTime.now();

        // OverviewData 집계
        OverviewData overview = buildOverviewDataByActivity(activityId, start, end);

        // Previous OverviewData (for comparison)
        LocalDateTime previousStart = start.minusDays(period.getDays());
        LocalDateTime previousEnd = end.minusDays(period.getDays());
        OverviewData previousOverview = buildOverviewDataByActivity(activityId, previousStart, previousEnd);

        // FunnelStepData 집계 (Universal 4-stage funnel)
        List<FunnelStep> funnelSteps = List.of(
                FunnelStep.VISIT,
                FunnelStep.ENGAGE,
                FunnelStep.QUALIFY,
                FunnelStep.PURCHASE);
        List<FunnelStepData> funnel = buildFunnelByActivity(activityId, funnelSteps, start, end);

        // Traffic Trend (Hourly)
        List<TimeSeriesData> trafficTrend = getTrafficTrend(activityId, start, end);

        // Realtime Data 집계
        RealtimeData realtime = buildRealtimeDataByActivity(activityId);

        // TODO: 응답 조합해서 return
        return new DashboardResponse(
                activityId,
                period.getCode(),
                LocalDateTime.now(),
                overview,
                previousOverview,
                funnel,
                trafficTrend,
                realtime);
    }

    private OverviewData buildOverviewDataByActivity(Long activityId, LocalDateTime start, LocalDateTime end) {
        // Funnel counts
        Long visits = getStepCount(activityId, FunnelStep.VISIT, start, end);
        Long engages = getStepCount(activityId, FunnelStep.ENGAGE, start, end);
        Long qualifies = getStepCount(activityId, FunnelStep.QUALIFY, start, end);
        Long purchases = getStepCount(activityId, FunnelStep.PURCHASE, start, end);

        // Fetch activity for price and budget
        com.axon.core_service.domain.campaignactivity.CampaignActivity activity =
                campaignActivityRepository.findById(activityId).orElse(null);

        java.math.BigDecimal price = activity != null ? activity.getPrice() : java.math.BigDecimal.valueOf(10000);
        java.math.BigDecimal budget = activity != null && activity.getBudget() != null
                ? activity.getBudget()
                : java.math.BigDecimal.ZERO;

        // Marketing metrics calculations
        java.math.BigDecimal gmv = price.multiply(java.math.BigDecimal.valueOf(purchases));
        java.math.BigDecimal aov = purchases > 0
                ? gmv.divide(java.math.BigDecimal.valueOf(purchases), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

        // Conversion rates (각 단계별 전환율)
        double conversionRate = visits > 0 ? (purchases * 100.0) / visits : 0.0;
        double engagementRate = visits > 0 ? (engages * 100.0) / visits : 0.0;
        double qualificationRate = engages > 0 ? (qualifies * 100.0) / engages : 0.0;
        double purchaseRate = qualifies > 0 ? (purchases * 100.0) / qualifies : 0.0;

        // ROAS (Return on Ad Spend)
        double roas = calculateROAS(gmv, budget);

        return new OverviewData(
                visits,
                engages,
                qualifies,
                purchases,
                gmv,
                conversionRate,
                engagementRate,
                qualificationRate,
                purchaseRate,
                aov,
                budget,
                roas
        );
    }

    private List<FunnelStepData> buildFunnelByActivity(Long activityId,
            List<FunnelStep> funnelSteps,
            LocalDateTime start,
            LocalDateTime end) {
        // helper 사용으로 중복 제거 및 간결화
        return funnelSteps.stream()
                .map(step -> new FunnelStepData(step, getStepCount(activityId, step, start, end)))
                .toList();
    }

    private List<TimeSeriesData> getTrafficTrend(Long activityId, LocalDateTime start, LocalDateTime end) {
        try {
            // Use singleton list for activityId
            java.util.Map<Integer, Long> hourlyTraffic = behaviorEventService.getHourlyTraffic(List.of(activityId),
                    start, end);

            // Convert Map<Integer, Long> (Hour -> Count) to List<TimeSeriesData>
            // Note: The current getHourlyTraffic returns aggregated by hour of day (0-23),
            // which might not be ideal for multi-day trends.
            // But for "Today" or "Last 24h", it's okay. For longer periods, we might need
            // full timestamp.
            // The wireframe shows "Time of day" for 24h view.
            // Let's map it to today's date for visualization if it's just hour.
            // Or better, let's assume the service returns data we can map.
            // Actually, getHourlyTraffic returns Map<Integer, Long> where Integer is 0-23.
            // We should probably map this to specific timestamps if possible, or just
            // return as is and let frontend handle.
            // But TimeSeriesData expects LocalDateTime.
            // Let's map hour to today's hour for now, or start date's hour.
            // A better approach for the future is to have getHourlyTraffic return
            // Map<LocalDateTime, Long>.
            // For now, let's just map 0-23 to the current day's hours for simplicity in
            // this MVP step.

            List<TimeSeriesData> trend = new ArrayList<>();
            LocalDateTime baseDate = LocalDate.now().atStartOfDay();

            for (Map.Entry<Integer, Long> entry : hourlyTraffic.entrySet()) {
                trend.add(new TimeSeriesData(baseDate.withHour(entry.getKey()), entry.getValue()));
            }
            return trend;

        } catch (IOException e) {
            log.error("Failed to get traffic trend for activity: {}", activityId, e);
            return Collections.emptyList();
        }
    }

    private RealtimeData buildRealtimeDataByActivity(Long activityId) {
        // Redis에서 실시간 Activity 데이터 수집
        Long participantCount = realtimeMetricsService.getParticipantCount(activityId);
        Long remainingStock = realtimeMetricsService.getRemainingStock(activityId);

        Long totalStock = campaignActivityRepository.findById(activityId)
                .map(com.axon.core_service.domain.campaignactivity.CampaignActivity::getLimitCount)
                .map(Long::valueOf)
                .orElse(100L); // Default if not found

        ActivityRealtime activityRealtime = new ActivityRealtime(participantCount, remainingStock, totalStock);
        return new RealtimeData(activityRealtime, LocalDateTime.now());
    }

    // == Helper method to reduce duplication and centralize exception handling
    /**
     * Gets event count for a specific funnel step.
     * Maps universal funnel steps to activity-specific events.
     */
    private Long getStepCount(Long activityId, FunnelStep step,
            LocalDateTime start, LocalDateTime end) {
        try {
            return switch (step) {
                case VISIT -> behaviorEventService.getVisitCount(activityId, start, end);
                case ENGAGE -> behaviorEventService.getEngageCount(activityId, start, end);
                case QUALIFY -> behaviorEventService.getQualifyCount(activityId, start, end);
                case PURCHASE -> behaviorEventService.getPurchaseCount(activityId, start, end);
            };
        } catch (IOException e) {
            log.error("Failed to get count for step: {} in activity: {}", step, activityId, e);
            return 0L;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Level 2: Campaign Overview & Comparison
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public CampaignDashboardResponse getDashboardByCampaign(Long campaignId) {
        // 1. Fetch Campaign and Activities
        com.axon.core_service.domain.campaign.Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        List<com.axon.core_service.domain.campaignactivity.CampaignActivity> activities = campaign
                .getCampaignActivities();

        // 2. Aggregate Overview & Build Comparison Table
        long totalVisits = 0;
        long totalEngages = 0;
        long totalQualifies = 0;
        long totalPurchases = 0;
        java.math.BigDecimal totalGMV = java.math.BigDecimal.ZERO;

        List<ActivityComparisonData> comparisonTable = new ArrayList<>();
        List<Long> activityIds = new ArrayList<>();
        LocalDateTime start = campaign.getStartAt() != null ? campaign.getStartAt() : LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        for (com.axon.core_service.domain.campaignactivity.CampaignActivity activity : activities) {
            activityIds.add(activity.getId());

            Long visits = getStepCount(activity.getId(), FunnelStep.VISIT, start, end);
            Long engages = getStepCount(activity.getId(), FunnelStep.ENGAGE, start, end);
            Long qualifies = getStepCount(activity.getId(), FunnelStep.QUALIFY, start, end);
            Long purchases = getStepCount(activity.getId(), FunnelStep.PURCHASE, start, end);

            // Calculate GMV
            java.math.BigDecimal gmv = calculateGMV(activity.getId(), purchases);

            // Calculate Conversion Rate (Visit -> Purchase)
            double conversionRate = visits > 0 ? (double) purchases / visits * 100 : 0.0;

            totalVisits += visits;
            totalEngages += engages;
            totalQualifies += qualifies;
            totalPurchases += purchases;
            totalGMV = totalGMV.add(gmv);

            comparisonTable.add(new ActivityComparisonData(
                    activity.getId(),
                    activity.getName(),
                    activity.getActivityType().name(),
                    visits,
                    purchases,
                    gmv,
                    conversionRate));
        }

        // Calculate Total Conversion Rate
        double totalConversionRate = totalVisits > 0 ? (double) totalPurchases / totalVisits * 100 : 0.0;
        double totalEngagementRate = totalVisits > 0 ? (double) totalEngages / totalVisits * 100 : 0.0;
        double totalQualificationRate = totalEngages > 0 ? (double) totalQualifies / totalEngages * 100 : 0.0;
        double totalPurchaseRate = totalQualifies > 0 ? (double) totalPurchases / totalQualifies * 100 : 0.0;

        // Calculate Total AOV
        java.math.BigDecimal totalAOV = totalPurchases > 0
            ? totalGMV.divide(java.math.BigDecimal.valueOf(totalPurchases), 2, java.math.RoundingMode.HALF_UP)
            : java.math.BigDecimal.ZERO;

        // For campaign level, budget is sum of all activity budgets (or campaign.budget if exists)
        java.math.BigDecimal totalBudget = campaign.getBudget() != null
            ? campaign.getBudget()
            : java.math.BigDecimal.ZERO;

        // Calculate Total ROAS
        double totalROAS = calculateROAS(totalGMV, totalBudget);

        OverviewData totalOverview = new OverviewData(
                totalVisits,
                totalEngages,
                totalQualifies,
                totalPurchases,
                totalGMV,
                totalConversionRate,
                totalEngagementRate,
                totalQualificationRate,
                totalPurchaseRate,
                totalAOV,
                totalBudget,
                totalROAS);

        // 3. Build Heatmap
        HeatmapData heatmap = null;
        try {
            java.util.Map<Integer, Long> hourlyTraffic = behaviorEventService.getHourlyTraffic(activityIds, start, end);
            heatmap = new HeatmapData(hourlyTraffic);
        } catch (IOException e) {
            log.error("Failed to fetch heatmap data for campaign: {}", campaignId, e);
            heatmap = new HeatmapData(java.util.Collections.emptyMap());
        }

        return new CampaignDashboardResponse(
                campaign.getId(),
                campaign.getName(),
                totalOverview,
                comparisonTable,
                heatmap,
                LocalDateTime.now());
    }

    public java.math.BigDecimal calculateGMV(Long activityId, Long purchaseCount) {
        // TODO: Fetch product price from CampaignActivity -> Product
        // For now, assuming a fixed price or fetching from a service
        java.math.BigDecimal price = java.math.BigDecimal.valueOf(10000); // Mock price
        return price.multiply(java.math.BigDecimal.valueOf(purchaseCount));
    }

    public double calculateROAS(java.math.BigDecimal gmv, java.math.BigDecimal budget) {
        if (budget == null || budget.compareTo(java.math.BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return gmv.divide(budget, 2, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
    }
}
