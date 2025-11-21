package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
                Long activityId,
                String period,
                LocalDateTime timestamp,
                OverviewData overview,
                OverviewData previousOverview,
                List<FunnelStepData> funnel,
                List<TimeSeriesData> trafficTrend,
                RealtimeData realtime) {
}
