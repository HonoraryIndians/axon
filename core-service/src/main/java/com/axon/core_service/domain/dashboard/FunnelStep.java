package com.axon.core_service.domain.dashboard;

public enum FunnelStep {
    VISIT,    // BehaviorEventService.getVisitCount()
    CLICK,    // BehaviorEventService.getClickCount()
    APPROVED, // CampaignMetricsService.getApprovedCount()
    PURCHASE  // CampaignMetricsService.getPurchaseCount()
}
