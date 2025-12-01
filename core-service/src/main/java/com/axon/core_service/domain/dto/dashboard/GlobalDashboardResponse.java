package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record GlobalDashboardResponse(
        OverviewData totalOverview,
        List<CampaignRankData> topCampaignsByGmv,
        List<CampaignRankData> topCampaignsByVisits,
        List<CampaignEfficiencyData> efficiencyData,
        LocalDateTime calculatedAt
) {}
