package com.axon.core_service.service.behavior;

import com.axon.core_service.adapter.BehaviorEventAdapter;
import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.event.UserLoginEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to backend domain events and publishes them to Kafka as behavior
 * events.
 *
 * This allows backend state changes (like purchase completion, user login) to
 * be tracked
 * in the same analytics pipeline as frontend user interactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BehaviorEventAdapter adapter;

    /**
     * Handle CampaignActivityApprovedEvent and publish it to Kafka as a purchase
     * behavior event.
     *
     * Executed asynchronously to avoid blocking the transaction that published the
     * event.
     *
     * @param event the campaign activity approved domain event
     */
    @Async
    @EventListener
    public void handleCampaignActivityApproved(CampaignActivityApprovedEvent event) {
        log.info("Publishing backend purchase event for userId={} activityId={} productId={}",
                event.userId(), event.campaignActivityId(), event.productId());

        UserBehaviorEventMessage message = adapter.toPurchaseEvent(event);

        kafkaTemplate.send(KafkaTopics.EVENT_RAW, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish backend purchase event for userId={} activityId={}",
                        event.userId(), event.campaignActivityId(), ex);
            } else {
                log.debug("Published backend purchase event to topic={} offset={}",
                        result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Handle UserLoginEvent and publish it to Kafka as a VISIT behavior event.
     *
     * Executed asynchronously to avoid blocking the authentication flow.
     *
     * @param event the user login domain event
     */
    @Async
    @EventListener
    public void handleUserLogin(UserLoginEvent event) {
        log.info("Publishing backend login event for userId={} loggedAt={}",
                event.userId(), event.loggedAt());

        UserBehaviorEventMessage message = adapter.toLoginEvent(event);

        kafkaTemplate.send(KafkaTopics.EVENT_RAW, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish backend login event for userId={}",
                        event.userId(), ex);
            } else {
                log.debug("Published backend login event to topic={} offset={}",
                        result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
            }
        });
    }
}
