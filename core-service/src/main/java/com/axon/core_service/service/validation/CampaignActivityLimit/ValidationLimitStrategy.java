package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.messaging.dto.validation.ValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ValidationLimitStrategy {
    /**
 * Retrieves the name that identifies this limit validation strategy.
 *
 * @return the name identifying this validation limit strategy
 */
String getLimitName();
    /**
 * Validate campaign activity limits for a user against the provided criteria.
 *
 * @param userId  the ID of the user to validate
 * @param operator a comparison operator (e.g., ">", "<", "=") to apply to the limits
 * @param limit   a list of limit criteria or constraints to evaluate
 * @return        a ValidationResponse describing the outcome (success or failure and related details)
 */
ValidationResponse validateCampaignActivityLimit(Long userId, String operator, List<String> limit);
}