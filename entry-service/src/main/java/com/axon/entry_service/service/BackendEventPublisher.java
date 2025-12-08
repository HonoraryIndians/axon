package com.axon.entry_service.service;

import com.axon.entry_service.adapter.BehaviorEventAdapter;
import com.axon.entry_service.event.ReservationApprovedEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to backend domain events from entry-service and publishes them to
 * Kafka.
 *
 * This allows backend state changes (like reservation approval) to be tracked
 * in the same analytics pipeline as frontend user interactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BehaviorEventAdapter adapter;

    /**
     * Handle ReservationApprovedEvent and publish it to Kafka as an APPROVED
     * behavior event.
     *
     * Executed asynchronously to avoid blocking the reservation transaction.
     *
     * @param event the reservation approved domain event
     */
    @Async
    @EventListener
    public void handleReservationApproved(ReservationApprovedEvent event) {
        log.info("Publishing backend APPROVED event for userId={} activityId={} order={}",
                event.userId(), event.campaignActivityId(), event.order());

        // 1. Publish to Behavior Events (Elasticsearch)
        UserBehaviorEventMessage message = adapter.toApprovedEvent(event);

        kafkaTemplate.send(KafkaTopics.BEHAVIOR_EVENT, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish backend APPROVED event for userId={} activityId={}",
                        event.userId(), event.campaignActivityId(), ex);
            } else {
                log.debug("Published backend APPROVED event to topic={} offset={}",
                        result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
            }
        });
    }
}
