package com.axon.core_service.service;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dashboard.FunnelStep;
import com.axon.core_service.domain.dto.dashboard.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final CampaignMetricsService campaignMetricsService;
    private final RealtimeMetricsService realtimeMetricsService;
    private final BehaviorEventService behaviorEventService;

    public DashboardResponse getDashboardByActivity(Long activityId, DashboardPeriod period,
                                                   LocalDateTime customStart, LocalDateTime customEnd) {

        LocalDateTime start = customStart != null ?
                customStart
                : period.getStartDateTime();
        LocalDateTime end = LocalDateTime.now();

        // OverviewData 집계
        OverviewData overview = buildOverviewDataByActivity(activityId, start, end);

        // FunnelStepData 집계
        List<FunnelStep> funnelSteps = List.of(
                FunnelStep.VISIT,
                FunnelStep.CLICK,
                FunnelStep.APPROVED,
                FunnelStep.PURCHASE
        );
        List<FunnelStepData> funnel = buildFunnelByActivity(activityId, funnelSteps, start, end);

        // Realtime Data 집계
        RealtimeData realtime = buildRealtimeDataByActivity(activityId);

        //TODO: 응답 조합해서 return
        return new DashboardResponse(
                activityId,
                period.getCode(),
                LocalDateTime.now(),
                overview,
                funnel,
                realtime
        );
    }
    private OverviewData buildOverviewDataByActivity(Long activityId, LocalDateTime start, LocalDateTime end) {
        // 각 서비스에서 데이터 수집 후 조합 (helper 사용으로 중복 제거)
        return new OverviewData(
                getStepCount(activityId, FunnelStep.VISIT, start, end),
                getStepCount(activityId, FunnelStep.CLICK, start, end),
                getStepCount(activityId, FunnelStep.APPROVED, start, end),
                getStepCount(activityId, FunnelStep.PURCHASE, start, end)
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
    private RealtimeData buildRealtimeDataByActivity(Long activityId) {
        // Redis에서 실시간 Activity 데이터 수집
        Long participantCount = realtimeMetricsService.getParticipantCount(activityId);
        Long remainingStock = realtimeMetricsService.getRemainingStock(activityId);

        ActivityRealtime activityRealtime = new ActivityRealtime(participantCount, remainingStock);
        return new RealtimeData(activityRealtime, LocalDateTime.now());
    }

    // == Helper method to reduce duplication and centralize exception handling
    private Long getStepCount(Long activityId, FunnelStep step,
                             LocalDateTime start, LocalDateTime end) {
        try {
            return switch (step) {
                case VISIT -> behaviorEventService.getVisitCount(activityId, start, end);
                case CLICK -> behaviorEventService.getClickCount(activityId, start, end);
                case APPROVED -> campaignMetricsService.getApprovedCount(activityId, start, end);
                case PURCHASE -> campaignMetricsService.getPurchaseCount(activityId, start, end);
            };
        } catch (IOException e) {
            log.error("Failed to get count for step: {} in activity: {}", step, activityId, e);
            return 0L;
        }
    }

}
