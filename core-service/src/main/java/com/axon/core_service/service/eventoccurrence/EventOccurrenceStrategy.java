package com.axon.core_service.service.eventoccurrence;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;

public interface EventOccurrenceStrategy {
    /**
 * Gets the trigger type handled by this strategy.
 *
 * @return the trigger type identifier handled by this strategy
 */
String getTriggerType();
    /**
 * Creates or records an event occurrence using the provided request data.
 *
 * <p>The implementation should process the details in the {@code request} and persist or dispatch
 * the resulting event occurrence according to the strategy's trigger type.</p>
 *
 * @param request the data describing the event occurrence to create
 */
void createEventOccurrence(EventOccurrenceRequest request);
}