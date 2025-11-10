package com.axon.core_service.service.eventoccurrence;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventOccurrence;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import com.axon.core_service.repository.EventOccurrenceRepository;
import com.axon.core_service.repository.EventRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseTriggerStrategy implements EventOccurrenceStrategy {
    private final EventOccurrenceRepository eventOccurrenceRepository;
    private final EventRepository eventRepository;

    /**
     * Identifies the trigger type handled by this strategy.
     *
     * @return the trigger type name "Purchase"
     */
    @Override
    public String getTriggerType() {
        return "Purchase";
    }

    /**
     * Creates and persists an EventOccurrence from the given request by resolving the associated Event and populating occurrence fields.
     *
     * The occurrence timestamp is taken from request.getOccurredAt() if present, otherwise the current time. If request.getPageUrl() is null or empty, the stored pageUrl will be null.
     *
     * @param request the data used to resolve the Event and populate the EventOccurrence
     */
    @Override
    @Transactional
    public void createEventOccurrence(EventOccurrenceRequest request) {
        Event event = resolveEvent(request);
        LocalDateTime occurredAt = request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now();

        EventOccurrence eventOccurrence = EventOccurrence.builder()
                .event(event)
                .occurredAt(occurredAt)
                .userId(request.getUserId())
                .pageUrl(request.getPageUrl() == null ||  request.getPageUrl().isEmpty() ? null : request.getPageUrl())
                .context(request.getContext())
                .build();
        eventOccurrenceRepository.save(eventOccurrence);
    }

    /**
     * Resolve the Event to associate with the occurrence described by the request.
     *
     * @param request the event occurrence request containing an optional eventId; if `eventId` is present the method resolves that event, otherwise it selects the first active event with trigger type PURCHASE
     * @return the resolved Event
     * @throws IllegalArgumentException if `eventId` is provided but no Event with that id exists
     * @throws IllegalStateException if no active PURCHASE trigger Event is available when `eventId` is not provided
     */
    private Event resolveEvent(EventOccurrenceRequest request) {
        if (request.getEventId() != null) {
            return eventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new IllegalArgumentException("유효한 이벤트가 아닙니다."));
        }
        return eventRepository.findFirstByTriggerTypeAndStatus(TriggerType.PURCHASE, EventStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("활성화된 구매 트리거 이벤트가 없습니다."));
    }
}