package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        Long activityId,
        String period,
        LocalDateTime timestamp,
        OverviewData overview,
        List<FunnelStepData> funnel,
        RealtimeData realtime
) {
}
