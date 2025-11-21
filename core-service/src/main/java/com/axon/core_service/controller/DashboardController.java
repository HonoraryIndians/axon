package com.axon.core_service.controller;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dto.dashboard.DashboardResponse;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // View Controllers - Moved to DashboardViewController
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // REST Endpoints (one-time fetch)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
    public ResponseEntity<com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse> getDashboardByCampaign(
            @PathVariable Long campaignId) {
        com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse response = dashboardService
                .getDashboardByCampaign(campaignId);
        return ResponseEntity.ok(response);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SSE Endpoints (real-time streaming)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
