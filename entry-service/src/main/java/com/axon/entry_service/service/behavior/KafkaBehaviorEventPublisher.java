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
