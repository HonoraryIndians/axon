package com.axon.core_service.adapter;

import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.event.UserLoginEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that converts core-service domain events to CDP standard format.
 * Follows the Adapter pattern to keep domain events separate from CDP
 * infrastructure.
 */
@Component
public class BehaviorEventAdapter {

    private static final String BACKEND_USER_AGENT = "Axon-Backend/1.0";
    private static final String PURCHASE_TRIGGER_TYPE = "PURCHASE";
    private static final String VISIT_TRIGGER_TYPE = "VISIT";

    /**
     * Convert CampaignActivityApprovedEvent to CDP standard
     * UserBehaviorEventMessage.
     *
     * @param event the campaign activity approved domain event
     * @return CDP standard behavior event message ready for Kafka publishing
     */
    public UserBehaviorEventMessage toPurchaseEvent(CampaignActivityApprovedEvent event) {
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
     * Convert UserLoginEvent to CDP standard UserBehaviorEventMessage.
     *
     * @param event the user login domain event
     * @return CDP standard behavior event message representing the login as a VISIT
     *         event
     */
    public UserBehaviorEventMessage toLoginEvent(UserLoginEvent event) {
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
     * @param activityId the campaign activity ID
     * @return a synthetic URL like "/backend/purchase/123"
     */
    private String generatePurchaseUrl(Long activityId) {
        return "/backend/purchase/" + activityId;
    }
}
