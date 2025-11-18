package com.axon.core_service.service.behavior;

import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.event.UserLoginEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BackendEventFactory 테스트")
class BackendEventFactoryTest {

    private final BackendEventFactory factory = new BackendEventFactory();

    @Test
    @DisplayName("CampaignActivityApprovedEvent를 UserBehaviorEventMessage로 변환")
    void createPurchaseEvent_Success() {
        // given
        Long userId = 100L;
        Long activityId = 200L;
        Long productId = 300L;
        Instant occurredAt = Instant.parse("2025-11-18T10:00:00Z");

        CampaignActivityApprovedEvent event = new CampaignActivityApprovedEvent(
                activityId, userId, productId, occurredAt
        );

        // when
        UserBehaviorEventMessage message = factory.createPurchaseEvent(event);

        // then
        assertThat(message.getEventName()).isEqualTo("purchase_completed");
        assertThat(message.getTriggerType()).isEqualTo("PURCHASE");
        assertThat(message.getUserId()).isEqualTo(userId);
        assertThat(message.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(message.getPageUrl()).isEqualTo("/backend/purchase/200");
        assertThat(message.getUserAgent()).isEqualTo("Axon-Backend/1.0");
        assertThat(message.getSessionId()).isNull();
        assertThat(message.getReferrer()).isNull();

        assertThat(message.getProperties()).isNotNull();
        assertThat(message.getProperties().get("activityId")).isEqualTo(activityId);
        assertThat(message.getProperties().get("productId")).isEqualTo(productId);
        assertThat(message.getProperties().get("source")).isEqualTo("backend");
    }

    @Test
    @DisplayName("UserLoginEvent를 UserBehaviorEventMessage (VISIT)로 변환")
    void createLoginEvent_Success() {
        // given
        Long userId = 100L;
        Instant loggedAt = Instant.parse("2025-11-18T10:00:00Z");

        UserLoginEvent loginEvent = new UserLoginEvent(userId, loggedAt);

        // when
        UserBehaviorEventMessage message = factory.createLoginEvent(loginEvent);

        // then
        assertThat(message.getEventName()).isEqualTo("user_login");
        assertThat(message.getTriggerType()).isEqualTo("VISIT");
        assertThat(message.getUserId()).isEqualTo(userId);
        assertThat(message.getOccurredAt()).isEqualTo(loggedAt);
        assertThat(message.getPageUrl()).isEqualTo("/backend/login");
        assertThat(message.getUserAgent()).isEqualTo("Axon-Backend/1.0");
        assertThat(message.getSessionId()).isNull();
        assertThat(message.getReferrer()).isNull();

        assertThat(message.getProperties()).isNotNull();
        assertThat(message.getProperties().get("source")).isEqualTo("backend");
        assertThat(message.getProperties().get("eventType")).isEqualTo("login");
    }
}
