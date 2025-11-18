package com.axon.core_service.domain.dto.dashboard;

public record OverviewData (
        Long totalVisits,
        Long totalClicks,
        Long approvedCount,
        Long purchaseCount
){ }
