package com.axon.core_service.service.behavior;

import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import com.axon.messaging.topic.KafkaTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackendEventPublisher 테스트")
class BackendEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private BackendEventFactory eventFactory;

    @InjectMocks
    private BackendEventPublisher publisher;

    @Captor
    private ArgumentCaptor<UserBehaviorEventMessage> messageCaptor;

    @Test
    @DisplayName("CampaignActivityApprovedEvent를 받아서 Kafka로 발행")
    void handleCampaignActivityApproved_Success() {
        // given
        Long userId = 100L;
        Long activityId = 200L;
        Long productId = 300L;
        Instant now = Instant.now();

        CampaignActivityApprovedEvent event = new CampaignActivityApprovedEvent(
                activityId, userId, productId, now
        );

        UserBehaviorEventMessage expectedMessage = UserBehaviorEventMessage.builder()
                .eventName("purchase_completed")
                .triggerType("PURCHASE")
                .userId(userId)
                .occurredAt(now)
                .pageUrl("/backend/purchase/" + activityId)
                .build();

        when(eventFactory.createPurchaseEvent(event)).thenReturn(expectedMessage);
        when(kafkaTemplate.send(eq(KafkaTopics.EVENT_RAW), any(UserBehaviorEventMessage.class)))
                .thenReturn(new CompletableFuture<>());

        // when
        publisher.handleCampaignActivityApproved(event);

        // then
        verify(eventFactory).createPurchaseEvent(event);
        verify(kafkaTemplate).send(eq(KafkaTopics.EVENT_RAW), messageCaptor.capture());

        UserBehaviorEventMessage capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getEventName()).isEqualTo("purchase_completed");
        assertThat(capturedMessage.getTriggerType()).isEqualTo("PURCHASE");
        assertThat(capturedMessage.getUserId()).isEqualTo(userId);
        assertThat(capturedMessage.getOccurredAt()).isEqualTo(now);
    }
}
