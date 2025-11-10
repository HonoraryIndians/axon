package com.axon.entry_service.service.behavior;

import com.axon.entry_service.domain.behavior.UserBehaviorEvent;
import com.axon.messaging.dto.UserBehaviorEventMessage;
import com.axon.messaging.topic.KafkaTopics;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaBehaviorEventPublisher implements BehaviorEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish the given user behavior event to the raw Kafka topic.
     *
     * Maps the event to a transport message and sends it to Kafka topic {@code KafkaTopics.EVENT_RAW}.
     * On send completion, logs an error with the event ID and trigger type if publishing fails,
     * or logs the topic and offset when publishing succeeds.
     *
     * @param event the user behavior event to publish
     */
    @Override
    public void publish(UserBehaviorEvent event) {
        UserBehaviorEventMessage message = mapToMessage(event);
        log.info("publishing behavior event");
        kafkaTemplate.send(KafkaTopics.EVENT_RAW, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish behavior event. eventId={} triggerType={}",
                        event.getEventId(), event.getTriggerType(), ex);
            } else if (log.isDebugEnabled()) {
                log.debug("Published behavior event to topic={} offset={}",
                        result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Convert a UserBehaviorEvent into a UserBehaviorEventMessage.
     *
     * <p>If the source event's properties map is empty, the message's `properties` field is set to
     * `null` instead of an empty map.</p>
     *
     * @param event the source event to map
     * @return the mapped UserBehaviorEventMessage
     */
    private UserBehaviorEventMessage mapToMessage(UserBehaviorEvent event) {
        Map<String, Object> props = event.getProperties().isEmpty()
                ? null
                : event.getProperties();

        Instant occurredAt = event.getOccurredAt();
        return UserBehaviorEventMessage.builder()
                .eventId(event.getEventId())
                .eventName(event.getEventName())
                .triggerType(event.getTriggerType())
                .occurredAt(occurredAt)
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .pageUrl(event.getPageUrl())
                .referrer(event.getReferrer())
                .userAgent(event.getUserAgent())
                .properties(props)
                .build();
    }
}