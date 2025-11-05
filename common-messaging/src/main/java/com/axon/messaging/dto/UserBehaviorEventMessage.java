package com.axon.messaging.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserBehaviorEventMessage {

    private Long eventId;
    private String eventName;
    private String triggerType;
    private Instant occurredAt;
    private Long userId;
    private String sessionId;
    private String pageUrl;
    private String referrer;
    private String userAgent;
    private Map<String, Object> properties;
}
