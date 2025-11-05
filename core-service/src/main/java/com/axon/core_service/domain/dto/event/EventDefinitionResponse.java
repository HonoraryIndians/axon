package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.TriggerType;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventDefinitionResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final TriggerType triggerType;
    private final Map<String, Object> triggerPayload;

    public static EventDefinitionResponse from(Event event) {
        return EventDefinitionResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .triggerType(event.getTriggerCondition().getTriggerType())
                .triggerPayload(event.getTriggerCondition().getPayload())
                .build();
    }
}
