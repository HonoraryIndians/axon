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

    /**
     * Create an EventDefinitionResponse populated from the given Event.
     *
     * @param event the source Event whose id, name, description, and triggerCondition are used to populate the response; its triggerCondition must be non-null
     * @return an EventDefinitionResponse with id, name, description, triggerType, and triggerPayload taken from the provided event
     */
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