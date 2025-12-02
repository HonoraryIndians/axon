package com.axon.entry_service.adapter;

import com.axon.entry_service.event.ReservationApprovedEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that converts entry-service domain events to CDP standard format.
 * Follows the Adapter pattern to keep domain events separate from CDP
 * infrastructure.
 */
@Component
public class BehaviorEventAdapter {

    private static final String BACKEND_USER_AGENT = "Axon-Backend/1.0";
    private static final String APPROVED_TRIGGER_TYPE = "APPROVED";

    @Value("${axon.core-service.base-url:http://localhost:8080}")
    private String coreServiceUrl;

    /**
     * Convert ReservationApprovedEvent to CDP standard UserBehaviorEventMessage.
     *
     * @param event the reservation approved domain event
     * @return CDP standard behavior event message ready for Kafka publishing
     */
    public UserBehaviorEventMessage toApprovedEvent(ReservationApprovedEvent event) {
        String syntheticUrl = generateApprovedUrl(event.campaignActivityId());
        Map<String, Object> properties = new HashMap<>();
        properties.put("activityId", event.campaignActivityId());
        properties.put("order", event.order());
        properties.put("source", "backend");

        return UserBehaviorEventMessage.builder()
                .eventName("reservation_approved")
                .triggerType(APPROVED_TRIGGER_TYPE)
                .occurredAt(event.occurredAt())
                .userId(event.userId())
                .sessionId(null) // Backend events don't have browser sessions
                .pageUrl(syntheticUrl)
                .referrer(null)
                .userAgent(BACKEND_USER_AGENT)
                .properties(properties)
                .build();
    }

    /**
     * Generate a synthetic URL for an approved event.
     * Uses the same URL pattern as frontend events to match wildcard queries
     * in BehaviorEventService
     *
     * @param activityId the campaign activity ID
     * @return a synthetic URL like "http://localhost:8080/campaign-activity/123/checkout"
     */
    private String generateApprovedUrl(Long activityId) {
        return coreServiceUrl + "/campaign-activity/" + activityId + "/checkout";
    }
}
