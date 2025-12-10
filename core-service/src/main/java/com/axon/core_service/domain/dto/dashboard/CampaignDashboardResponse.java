package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record CampaignDashboardResponse(
        Long campaignId,
        String campaignName,
        String period,
        OverviewData overview,
        OverviewData previousOverview,
        List<ActivityComparisonData> activities,
        HeatmapData heatmap,
        LocalDateTime calculatedAt) {
    public OverviewData getOverview() {
        return overview;
    }
}