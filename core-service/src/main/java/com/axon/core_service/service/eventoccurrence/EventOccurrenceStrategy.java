package com.axon.core_service.service.eventoccurrence;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;

public interface EventOccurrenceStrategy {
    String getTriggerType();
    void createEventOccurrence(EventOccurrenceRequest request);
}
