package com.axon.core_service.service.eventoccurrence;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventOccurrence;
import com.axon.core_service.repository.EventOccurrenceRepository;
import com.axon.core_service.repository.EventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseTriggerStrategy implements EventOccurrenceStrategy {
    private final EventOccurrenceRepository eventOccurrenceRepository;
    private final EventRepository eventRepository;

    @Override
    public String getTriggerType() {
        return "Purchase";
    }

    @Override
    @Transactional
    public void createEventOccurrence(EventOccurrenceRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("유효한 이벤트가 아닙니다."));

        EventOccurrence eventOccurrence = EventOccurrence.builder()
                .event(event)
                .occurredAt(request.getOccurredAt())
                .userId(request.getUserId())
                .pageUrl(request.getPageUrl() == null ||  request.getPageUrl().isEmpty() ? null : request.getPageUrl())
                .context(request.getContext())
                .build();
        eventOccurrenceRepository.save(eventOccurrence);
    }
}
