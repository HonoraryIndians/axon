package com.axon.core_service.service;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dashboard.FunnelStep;
import com.axon.core_service.domain.dto.dashboard.*;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CampaignRepository campaignRepository;
    private final CampaignActivityRepository campaignActivityRepository;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Level 3: Activity Detail Dashboard
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardByActivity(Long activityId, DashboardPeriod period,
            LocalDateTime customStart, LocalDateTime customEnd) {
        LocalDateTime start = customStart != null ? customStart
                : period.getStartDateTime();
        LocalDateTime end = LocalDateTime.now();

        OverviewData overview = buildOverviewDataByActivity(activityId, start, end);

        LocalDateTime previousStart = start.minusDays(period.getDays());
        LocalDateTime previousEnd = end.minusDays(period.getDays());
        OverviewData previousOverview = buildOverviewDataByActivity(activityId, previousStart, previousEnd);

        List<FunnelStep> funnelSteps = List.of(
                FunnelStep.VISIT,
                FunnelStep.ENGAGE,
                FunnelStep.QUALIFY,
                FunnelStep.PURCHASE);
        List<FunnelStepData> funnel = buildFunnelByActivity(activityId, funnelSteps, start, end);

        List<TimeSeriesData> trafficTrend = getTrafficTrend(activityId, start, end);
        RealtimeData realtime = buildRealtimeDataByActivity(activityId);

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
        Long visits = getStepCount(activityId, FunnelStep.VISIT, start, end);
        Long engages = getStepCount(activityId, FunnelStep.ENGAGE, start, end);
        Long qualifies = getStepCount(activityId, FunnelStep.QUALIFY, start, end);
        Long purchases = getStepCount(activityId, FunnelStep.PURCHASE, start, end);

        com.axon.core_service.domain.campaignactivity.CampaignActivity activity =
                campaignActivityRepository.findById(activityId).orElse(null);

        java.math.BigDecimal price = activity != null ? activity.getPrice() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal budget = activity != null && activity.getBudget() != null
                ? activity.getBudget()
                : java.math.BigDecimal.ZERO;

        java.math.BigDecimal gmv = price.multiply(java.math.BigDecimal.valueOf(purchases));
        java.math.BigDecimal aov = purchases > 0
                ? gmv.divide(java.math.BigDecimal.valueOf(purchases), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

        double conversionRate = visits > 0 ? (purchases * 100.0) / visits : 0.0;
        double engagementRate = visits > 0 ? (engages * 100.0) / visits : 0.0;
        double qualificationRate = engages > 0 ? (qualifies * 100.0) / engages : 0.0;
        double purchaseRate = qualifies > 0 ? (purchases * 100.0) / qualifies : 0.0;

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
        return funnelSteps.stream()
                .map(step -> new FunnelStepData(step, getStepCount(activityId, step, start, end)))
                .toList();
    }

    private List<TimeSeriesData> getTrafficTrend(Long activityId, LocalDateTime start, LocalDateTime end) {
        try {
            Map<Integer, Long> hourlyTraffic = behaviorEventService.getHourlyTraffic(List.of(activityId), start, end);
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
        Long participantCount = realtimeMetricsService.getParticipantCount(activityId);
        Long remainingStock = realtimeMetricsService.getRemainingStock(activityId);

        Long totalStock = campaignActivityRepository.findById(activityId)
                .map(com.axon.core_service.domain.campaignactivity.CampaignActivity::getLimitCount)
                .map(Long::valueOf)
                .orElse(100L);

        ActivityRealtime activityRealtime = new ActivityRealtime(participantCount, remainingStock, totalStock);
        return new RealtimeData(activityRealtime, LocalDateTime.now());
    }

    private Long getStepCount(Long activityId, FunnelStep step, LocalDateTime start, LocalDateTime end) {
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
    // Level 2: Campaign Overview & Comparison (Optimized)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Transactional(readOnly = true)
    public CampaignDashboardResponse getDashboardByCampaign(Long campaignId) {
        com.axon.core_service.domain.campaign.Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        List<com.axon.core_service.domain.campaignactivity.CampaignActivity> activities = campaign
                .getCampaignActivities();

        LocalDateTime start = campaign.getStartAt() != null ? campaign.getStartAt() : LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        // 2. Optimized: Fetch all stats in ONE query
        Map<Long, Map<String, Long>> stats;
        try {
            stats = behaviorEventService.getCampaignStats(campaignId, start, end);
        } catch (IOException e) {
            log.error("Failed to fetch campaign stats for id={}", campaignId, e);
            stats = Collections.emptyMap();
        }

        long totalVisits = 0;
        long totalEngages = 0;
        long totalQualifies = 0;
        long totalPurchases = 0;
        java.math.BigDecimal totalGMV = java.math.BigDecimal.ZERO;

        List<ActivityComparisonData> comparisonTable = new ArrayList<>();
        List<Long> activityIds = new ArrayList<>();

        for (com.axon.core_service.domain.campaignactivity.CampaignActivity activity : activities) {
            activityIds.add(activity.getId());
            Map<String, Long> activityStats = stats.getOrDefault(activity.getId(), Collections.emptyMap());

            Long visits = activityStats.getOrDefault("PAGE_VIEW", 0L);
            Long engages = activityStats.getOrDefault("CLICK", 0L);
            Long qualifies = activityStats.getOrDefault("APPROVED", 0L);
            Long purchases = activityStats.getOrDefault("PURCHASE", 0L);

            java.math.BigDecimal gmv = calculateGMV(activity.getId(), purchases);

            // Calculate Conversion Rate (Visit -> Purchase)
            double conversionRate = visits > 0 ? (double) purchases / visits * 100 : 0.0;
            // Calculate Engagement Rate (Visit -> Engage)
            double engagementRate = visits > 0 ? (double) engages / visits * 100 : 0.0;

            totalVisits += visits;
            totalEngages += engages;
            totalQualifies += qualifies;
            totalPurchases += purchases;
            totalGMV = totalGMV.add(gmv);

            String category = activity.getProduct() != null ? activity.getProduct().getCategory() : "Unknown";

            comparisonTable.add(new ActivityComparisonData(
                    activity.getId(),
                    activity.getName(),
                    activity.getActivityType().name(),
                    category,
                    visits,
                    engages,
                    purchases,
                    gmv,
                    engagementRate,
                    conversionRate));
        }

        double totalConversionRate = totalVisits > 0 ? (double) totalPurchases / totalVisits * 100 : 0.0;
        double totalEngagementRate = totalVisits > 0 ? (double) totalEngages / totalVisits * 100 : 0.0;
        double totalQualificationRate = totalEngages > 0 ? (double) totalQualifies / totalEngages * 100 : 0.0;
        double totalPurchaseRate = totalQualifies > 0 ? (double) totalPurchases / totalQualifies * 100 : 0.0;

        java.math.BigDecimal totalAOV = totalPurchases > 0
            ? totalGMV.divide(java.math.BigDecimal.valueOf(totalPurchases), 2, java.math.RoundingMode.HALF_UP)
            : java.math.BigDecimal.ZERO;

        java.math.BigDecimal totalBudget = campaign.getBudget() != null
            ? campaign.getBudget()
            : java.math.BigDecimal.ZERO;

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

        HeatmapData heatmap = null;
        try {
            Map<Integer, Long> hourlyTraffic = behaviorEventService.getHourlyTraffic(activityIds, start, end);
            heatmap = new HeatmapData(hourlyTraffic);
        } catch (IOException e) {
            heatmap = new HeatmapData(Collections.emptyMap());
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
        com.axon.core_service.domain.campaignactivity.CampaignActivity activity = campaignActivityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        
        java.math.BigDecimal price = activity.getPrice() != null ? activity.getPrice() : java.math.BigDecimal.ZERO;
        return price.multiply(java.math.BigDecimal.valueOf(purchaseCount));
    }

    public double calculateROAS(java.math.BigDecimal gmv, java.math.BigDecimal budget) {
        if (budget == null || budget.compareTo(java.math.BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return gmv.divide(budget, 2, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Level 3: Global Dashboard (Cross-Campaign Comparison)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Transactional(readOnly = true)
    public GlobalDashboardResponse getGlobalDashboard() {
        List<com.axon.core_service.domain.campaign.Campaign> campaigns = campaignRepository.findAll();
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        Map<Long, Map<String, Long>> allStats;
        try {
            allStats = behaviorEventService.getAllCampaignStats(start, end);
        } catch (IOException e) {
            log.error("Failed to fetch global stats", e);
            allStats = Collections.emptyMap();
        }

        long totalVisits = 0;
        long totalPurchases = 0;
        java.math.BigDecimal totalGMV = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalBudget = java.math.BigDecimal.ZERO;

        List<CampaignRankData> gmvRanking = new ArrayList<>();
        List<CampaignRankData> visitRanking = new ArrayList<>();
        List<CampaignEfficiencyData> efficiencyData = new ArrayList<>();

        for (com.axon.core_service.domain.campaign.Campaign campaign : campaigns) {
            Long campaignId = campaign.getId();
            Map<String, Long> stats = allStats.getOrDefault(campaignId, Collections.emptyMap());

            Long visits = stats.getOrDefault("PAGE_VIEW", 0L);
            Long purchases = stats.getOrDefault("PURCHASE", 0L);
            
            // Simplified GMV calculation: Purchases * Avg Price (First activity price)
            // Note: For precise global GMV, we should aggregate per activity, but for speed we approximate here
            java.math.BigDecimal avgPrice = java.math.BigDecimal.valueOf(10000); 
            if (!campaign.getCampaignActivities().isEmpty()) {
                 java.math.BigDecimal firstPrice = campaign.getCampaignActivities().get(0).getPrice();
                 if (firstPrice != null) avgPrice = firstPrice;
            }
            java.math.BigDecimal campaignGmv = avgPrice.multiply(java.math.BigDecimal.valueOf(purchases));

            java.math.BigDecimal budget = campaign.getBudget() != null ? campaign.getBudget() : java.math.BigDecimal.ZERO;
            double roas = calculateROAS(campaignGmv, budget);

            totalVisits += visits;
            totalPurchases += purchases;
            totalGMV = totalGMV.add(campaignGmv);
            totalBudget = totalBudget.add(budget);

            if (visits > 0 || purchases > 0) {
                gmvRanking.add(new CampaignRankData(campaignId, campaign.getName(), campaignGmv.longValue(), formatCurrency(campaignGmv)));
                visitRanking.add(new CampaignRankData(campaignId, campaign.getName(), visits, String.valueOf(visits)));
                efficiencyData.add(new CampaignEfficiencyData(campaignId, campaign.getName(), budget, campaignGmv, roas));
            }
        }

        gmvRanking.sort((a, b) -> Long.compare(b.value(), a.value()));
        visitRanking.sort((a, b) -> Long.compare(b.value(), a.value()));

        if (gmvRanking.size() > 10) gmvRanking = gmvRanking.subList(0, 10);
        if (visitRanking.size() > 10) visitRanking = visitRanking.subList(0, 10);

        OverviewData globalOverview = new OverviewData(
                totalVisits, 0L, 0L, totalPurchases, totalGMV,
                0.0, 0.0, 0.0, 0.0, java.math.BigDecimal.ZERO, totalBudget, 
                calculateROAS(totalGMV, totalBudget)
        );

        return new GlobalDashboardResponse(
                globalOverview,
                gmvRanking,
                visitRanking,
                efficiencyData,
                LocalDateTime.now()
        );
    }

    private String formatCurrency(java.math.BigDecimal amount) {
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.KOREA).format(amount);
    }
}