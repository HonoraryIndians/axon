package com.axon.core_service.domain.dashboard;

public enum FunnelStep {
    VISIT, // BehaviorEventService.getVisitCount() - Elasticsearch
    CLICK, // BehaviorEventService.getClickCount() - Elasticsearch
    APPROVED, // BehaviorEventService.getApprovedCount() - Elasticsearch
    PURCHASE // BehaviorEventService.getPurchaseCount() - Elasticsearch
}
