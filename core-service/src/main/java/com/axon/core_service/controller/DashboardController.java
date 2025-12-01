package com.axon.core_service.controller;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse;
import com.axon.core_service.domain.dto.dashboard.CohortAnalysisResponse;
import com.axon.core_service.domain.dto.dashboard.DashboardResponse;
import com.axon.core_service.service.CohortAnalysisService;
import com.axon.core_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
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

        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId,
                startDate,
                endDate
        );
        return ResponseEntity.ok(response);
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
