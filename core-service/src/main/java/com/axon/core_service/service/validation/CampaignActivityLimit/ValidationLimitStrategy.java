package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.messaging.dto.validation.ValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ValidationLimitStrategy {
    String getLimitName();
    ValidationResponse validateCampaignActivityLimit(Long userId, String operator, List<String> limit);
}
