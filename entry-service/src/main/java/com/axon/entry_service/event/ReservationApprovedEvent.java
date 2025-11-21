package com.axon.entry_service.event;

import java.time.Instant;

/**
 * Domain event published when a user's reservation is approved in
 * entry-service.
 * This event is internal to entry-service and will be converted to CDP standard
 * format
 * by BehaviorEventAdapter before publishing to Kafka.
 */
public record ReservationApprovedEvent(
        Long campaignActivityId,
        Long userId,
        Long order,
        Instant occurredAt) {
}
