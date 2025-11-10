package com.axon.entry_service.domain.behavior;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UserBehaviorEvent {

    private final Long eventId;
    private final String eventName;
    private final String triggerType;
    private final Instant occurredAt;
    private final Long userId;
    private final String sessionId;
    private final String pageUrl;
    private final String referrer;
    private final String userAgent;
    private final Map<String, Object> properties;

    /**
     * Creates a UserBehaviorEvent instance, normalizing optional fields to ensure non-null and immutable state.
     *
     * @param occurredAt the time the event occurred; if {@code null}, the current instant is used
     * @param properties a map of event properties; if {@code null} or empty an empty map is assigned,
     *                   otherwise a new insertion-ordered unmodifiable copy is created
     */
    @Builder
    private UserBehaviorEvent(Long eventId,
                              String eventName,
                              String triggerType,
                              Instant occurredAt,
                              Long userId,
                              String sessionId,
                              String pageUrl,
                              String referrer,
                              String userAgent,
                              Map<String, Object> properties) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.triggerType = triggerType;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.userId = userId;
        this.sessionId = sessionId;
        this.pageUrl = pageUrl;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.properties = properties == null || properties.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}