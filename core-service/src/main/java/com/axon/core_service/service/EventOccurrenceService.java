package com.axon.core_service.service;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import com.axon.core_service.domain.dto.event.EventOccurrenceResponse;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventOccurrence;
import com.axon.core_service.repository.EventOccurrenceRepository;
import com.axon.core_service.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventOccurrenceService {

    private final EventRepository eventRepository;
    private final EventOccurrenceRepository eventOccurrenceRepository;

    @Transactional
    public EventOccurrenceResponse record(EventOccurrenceRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + request.getEventId()));

        EventOccurrence occurrence = EventOccurrence.builder()
                .event(event)
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now())
                .userId(request.getUserId())
                .pageUrl(request.getPageUrl())
                .context(sanitizeContext(request.getContext()))
                .build();

        return EventOccurrenceResponse.from(eventOccurrenceRepository.save(occurrence));
    }

    private Map<String, Object> sanitizeContext(Map<String, Object> context) {
        return context == null || context.isEmpty() ? Map.of() : Map.copyOf(context);
    }
}
