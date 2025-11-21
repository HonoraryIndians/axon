package com.axon.core_service.domain.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDashboardResponse {
    private Long campaignId;
    private String campaignName;
    private OverviewData overview;
    private List<ActivityComparisonData> activityComparison;
    private HeatmapData heatmap;
    private LocalDateTime timestamp;
}
