package com.axon.core_service.controller;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dashboard.LTVBatch;
import com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse;
import com.axon.core_service.domain.dto.dashboard.CohortAnalysisResponse;
import com.axon.core_service.domain.dto.dashboard.DashboardResponse;
import com.axon.core_service.repository.LTVBatchRepository;
import com.axon.core_service.service.CohortAnalysisService;
import com.axon.core_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.axon.core_service.domain.dto.dashboard.GlobalDashboardResponse;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CohortAnalysisService cohortAnalysisService;
    private final LTVBatchRepository ltvBatchRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // View Controllers - Moved to DashboardViewController
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // REST Endpoints (one-time fetch)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @GetMapping("/overview")
    public ResponseEntity<GlobalDashboardResponse> getGlobalDashboard() {
        return ResponseEntity.ok(dashboardService.getGlobalDashboard());
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<DashboardResponse> getDashboardByActivity(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        DashboardPeriod dashboardPeriod = DashboardPeriod.fromCode(period);
        DashboardResponse response = dashboardService.getDashboardByActivity(
                activityId,
                dashboardPeriod,
                startDate,
                endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<CampaignDashboardResponse> getDashboardByCampaign(
            @PathVariable Long campaignId) {
        CampaignDashboardResponse response = dashboardService
                .getDashboardByCampaign(campaignId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get Cohort Analysis for an Activity
     *
     * 특정 Activity에서 획득한 고객들의 LTV, CAC, 재구매율 분석
     *
     * 배치 데이터 우선 조회 방식:
     * 1. 배치 테이블에 데이터가 있으면 즉시 반환 (0.1초)
     * 2. 없으면 실시간 계산 (Fallback, 느림)
     *
     * @param activityId Activity ID
     * @param startDate Cohort 시작일 (optional, 기본값: Activity 시작일)
     * @param endDate Cohort 종료일 (optional, 기본값: 현재)
     * @return Cohort 분석 결과
     */
    @GetMapping("/cohort/activity/{activityId}")
    public ResponseEntity<CohortAnalysisResponse> getCohortAnalysisByActivity(
            @PathVariable Long activityId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        log.info("Cohort analysis request - activityId: {}, startDate: {}, endDate: {}",
                activityId, startDate, endDate);

        // 1. 배치 데이터가 있는지 확인
        if (ltvBatchRepository.existsByCampaignActivityId(activityId)) {
            log.info("Returning cached batch data for activity {}", activityId);
            CohortAnalysisResponse response = buildResponseFromBatchData(activityId);
            return ResponseEntity.ok(response);
        }

        // 2. 배치 데이터 없으면 실시간 계산 (Fallback)
        log.warn("No batch data found for activity {}, calculating in real-time (slow)", activityId);
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId,
                startDate,
                endDate
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 배치 데이터로부터 CohortAnalysisResponse 생성
     */
    private CohortAnalysisResponse buildResponseFromBatchData(Long activityId) {
        List<LTVBatch> stats = ltvBatchRepository
                .findByCampaignActivityIdOrderByMonthOffsetAsc(activityId);

        if (stats.isEmpty()) {
            throw new IllegalStateException("No batch stats found for activity " + activityId);
        }

        // 최신 월 데이터 (가장 큰 monthOffset)
        LTVBatch latestStat = stats.getLast();

        // 30일/90일/365일 LTV 찾기 (monthOffset 기준: 1개월 = ~30일)
        BigDecimal ltv30d = findLtvByMonthOffset(stats, 1);   // 0개월 ~ 1개월
        BigDecimal ltv90d = findLtvByMonthOffset(stats, 3);   // 0개월 ~ 3개월
        BigDecimal ltv365d = findLtvByMonthOffset(stats, 12); // 0개월 ~ 12개월

        // 월별 상세 데이터 생성 (그래프용 - "2025년 7월" 형식)
        List<CohortAnalysisResponse.MonthlyDetail> monthlyDetails = stats.stream()
                .map(stat -> new CohortAnalysisResponse.MonthlyDetail(
                        stat.getMonthOffset(),
                        formatMonthLabel(stat.getCohortStartDate(), stat.getMonthOffset()),
                        stat.getCohortStartDate().plusMonths(stat.getMonthOffset()).toLocalDate().toString(),
                        stat.getLtvCumulative(),
                        stat.getMonthlyRevenue(),
                        stat.getCumulativeProfit(),
                        stat.getLtvCacRatio().doubleValue(),
                        stat.getIsBreakEven(),
                        stat.getActiveUsers()
                ))
                .toList();

        return new CohortAnalysisResponse(
                "activity-" + activityId,
                latestStat.getCampaignActivity().getName(),
                latestStat.getCohortStartDate(),
                LocalDateTime.now(),
                (long) latestStat.getCohortSize(),
                latestStat.getCampaignActivity().getBudget(),
                latestStat.getAvgCac(),
                ltv30d,
                ltv90d,
                ltv365d,
                latestStat.getLtvCumulative(),
                calculateRatio(ltv30d, latestStat.getAvgCac()),
                calculateRatio(ltv90d, latestStat.getAvgCac()),
                calculateRatio(ltv365d, latestStat.getAvgCac()),
                latestStat.getLtvCacRatio().doubleValue(),
                latestStat.getRepeatPurchaseRate().doubleValue(),
                latestStat.getAvgPurchaseFrequency().doubleValue(),
                latestStat.getAvgOrderValue(),
                latestStat.getCollectedAt(),
                monthlyDetails
        );
    }

    /**
     * 월 라벨 포맷: "2025년 7월"
     */
    private String formatMonthLabel(LocalDateTime cohortStartDate, int monthOffset) {
        LocalDateTime targetMonth = cohortStartDate.plusMonths(monthOffset);
        return targetMonth.getYear() + "년 " + targetMonth.getMonthValue() + "월";
    }

    /**
     * 특정 월 오프셋의 LTV 조회
     */
    private BigDecimal findLtvByMonthOffset(List<LTVBatch> stats, int targetMonth) {
        return stats.stream()
                .filter(s -> s.getMonthOffset() == targetMonth - 1) // 0-indexed
                .findFirst()
                .map(LTVBatch::getLtvCumulative)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * LTV/CAC 비율 계산
     */
    private Double calculateRatio(BigDecimal ltv, BigDecimal cac) {
        if (cac == null || cac.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return ltv.divide(cac, 2, RoundingMode.HALF_UP).doubleValue();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SSE Endpoints (real-time streaming)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Stream real-time dashboard updates for a specific campaign.
     */
    @GetMapping(value = "/stream/campaign/{campaignId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCampaignDashboard(@PathVariable Long campaignId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        log.info("SSE connection opened for campaign: {}", campaignId);

        ScheduledFuture<?> scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                CampaignDashboardResponse data = dashboardService.getDashboardByCampaign(campaignId);
                emitter.send(SseEmitter.event().name("dashboard-update").data(data));
            } catch (Exception e) {
                log.error("Error streaming dashboard for campaign: {}", campaignId, e);
                emitter.completeWithError(e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        emitter.onCompletion(() -> scheduledTask.cancel(true));
        emitter.onTimeout(() -> scheduledTask.cancel(true));
        emitter.onError((ex) -> scheduledTask.cancel(true));

        return emitter;
    }

    /**
     * Stream real-time dashboard updates for a specific campaign activity.
     *
     * Sends dashboard data every 5 seconds via Server-Sent Events (SSE).
     * Marketers can monitor FCFS campaigns in real-time without manual refresh.
     *
     * @param activityId the campaign activity ID to monitor
     * @param period     the time period for analytics (default: 7d)
     * @return SseEmitter that streams dashboard updates
     */
    @GetMapping(value = "/stream/activity/{activityId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamActivityDashboard(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "7d") String period) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // No timeout
        DashboardPeriod dashboardPeriod = DashboardPeriod.fromCode(period);

        log.info("SSE connection opened for activity: {}, period: {}", activityId, period);

        // Schedule periodic dashboard updates
        ScheduledFuture<?> scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                // Fetch latest dashboard data
                DashboardResponse data = dashboardService.getDashboardByActivity(
                        activityId,
                        dashboardPeriod,
                        null,
                        null);

                // Send data to client
                emitter.send(SseEmitter.event()
                        .name("dashboard-update")
                        .data(data));

                log.debug("Sent dashboard update for activity: {}", activityId);

            } catch (IOException e) {
                log.warn("SSE connection closed for activity: {}", activityId);
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Error streaming dashboard for activity: {}", activityId, e);
                emitter.completeWithError(e);
            }
        }, 0, 5, TimeUnit.SECONDS); // Initial delay 0, then every 5 seconds

        // Cleanup when connection closes
        emitter.onCompletion(() -> {
            scheduledTask.cancel(true);
            log.info("SSE completed for activity: {}", activityId);
        });

        emitter.onTimeout(() -> {
            scheduledTask.cancel(true);
            log.warn("SSE timeout for activity: {}", activityId);
        });

        emitter.onError((ex) -> {
            scheduledTask.cancel(true);
            log.error("SSE error for activity: {}", activityId, ex);
        });

        return emitter;
    }
}
