package com.axon.core_service.service.llm;

import com.axon.core_service.domain.dto.llm.DashboardQueryResponse;

public interface LLMQueryService {
    DashboardQueryResponse processQuery(Long campaignId, String query);
    DashboardQueryResponse processQueryByActivity(Long activityId, String query);
}