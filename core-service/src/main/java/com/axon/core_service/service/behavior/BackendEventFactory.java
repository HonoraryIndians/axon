package com.axon.core_service.service.behavior;

import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.event.UserLoginEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating UserBehaviorEventMessage instances from backend domain events.
 *
 * Generates synthetic URLs and metadata to align backend events with frontend tracking format.
 */
@Component
public class BackendEventFactory {

    private static final String BACKEND_USER_AGENT = "Axon-Backend/1.0";
    private static final String PURCHASE_TRIGGER_TYPE = "PURCHASE";
    private static final String VISIT_TRIGGER_TYPE = "VISIT";

    /**
     * Create a UserBehaviorEventMessage from a CampaignActivityApprovedEvent.
     *
     * @param event the campaign activity approved domain event
     * @return a behavior event message ready for Kafka publishing
     */
    public UserBehaviorEventMessage createPurchaseEvent(CampaignActivityApprovedEvent event) {
        String syntheticUrl = generatePurchaseUrl(event.campaignActivityId());
        Map<String, Object> properties = new HashMap<>();
        properties.put("activityId", event.campaignActivityId());
        properties.put("productId", event.productId());
        properties.put("source", "backend");

        return UserBehaviorEventMessage.builder()
                .eventName("purchase_completed")
                .triggerType(PURCHASE_TRIGGER_TYPE)
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
     * Create a UserBehaviorEventMessage from a UserLoginEvent.
     *
     * @param event the user login domain event
     * @return a behavior event message representing the login as a VISIT event
     */
    public UserBehaviorEventMessage createLoginEvent(UserLoginEvent event) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("source", "backend");
        properties.put("eventType", "login");

        return UserBehaviorEventMessage.builder()
                .eventName("user_login")
                .triggerType(VISIT_TRIGGER_TYPE)
                .occurredAt(event.loggedAt())
                .userId(event.userId())
                .sessionId(null) // Backend events don't have browser sessions
                .pageUrl("/backend/login")
                .referrer(null)
                .userAgent(BACKEND_USER_AGENT)
                .properties(properties)
                .build();
    }

    /**
     * Generate a synthetic URL for a purchase event.
     *
     * This URL doesn't correspond to an actual page, but provides a consistent format
     * for backend purchase events in the analytics pipeline.
     *
     * @param activityId the campaign activity ID
     * @return a synthetic URL like "/backend/purchase/123"
     */
    private String generatePurchaseUrl(Long activityId) {
        return "/backend/purchase/" + activityId;
    }
}
