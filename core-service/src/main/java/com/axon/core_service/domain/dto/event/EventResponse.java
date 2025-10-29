package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final EventStatus status;
    private final TriggerType triggerType;
    private final Map<String, Object> triggerPayload;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .status(event.getStatus())
                .triggerType(event.getTriggerCondition().getTriggerType())
                .triggerPayload(event.getTriggerCondition().getPayload())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
