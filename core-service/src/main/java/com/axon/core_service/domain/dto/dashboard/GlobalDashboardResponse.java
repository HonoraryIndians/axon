package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record GlobalDashboardResponse(
        OverviewData overview,
        List<CampaignRankData> topGmvCampaigns,
        List<CampaignRankData> topVisitCampaigns,
        List<CampaignEfficiencyData> campaignEfficiency,
        HeatmapData hourlyTraffic,
        LocalDateTime calculatedAt
) {}
