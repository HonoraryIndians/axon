package com.axon.core_service.service;

import com.axon.core_service.domain.dto.event.EventDefinitionResponse;
import com.axon.core_service.domain.dto.event.EventRequest;
import com.axon.core_service.domain.dto.event.EventResponse;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import com.axon.core_service.repository.EventRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private final EventRepository eventRepository;

    public EventResponse createEvent(EventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus())
                .triggerCondition(Event.TriggerCondition.of(
                        request.getTriggerType(),
                        sanitizePayload(request.getTriggerPayload())
                ))
                .build();

        return EventResponse.from(eventRepository.save(event));
    }

    public EventResponse updateEvent(Long eventId, EventRequest request) {
        Event event = getEventEntity(eventId);
        event.updateDetails(request.getName(), request.getDescription());
        event.updateTriggerCondition(
                request.getTriggerType(),
                sanitizePayload(request.getTriggerPayload())
        );
        if (request.getStatus() != null) {
            event.changeStatus(request.getStatus());
        }
        return EventResponse.from(event);
    }

    public EventResponse updateEventDetails(Long eventId, String name, String description) {
        Event event = getEventEntity(eventId);
        event.updateDetails(name, description);
        return EventResponse.from(event);
    }

    public EventResponse changeStatus(Long eventId, EventStatus status) {
        Event event = getEventEntity(eventId);
        event.changeStatus(status);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return EventResponse.from(getEventEntity(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents() {
        return eventRepository.findAll().stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventDefinitionResponse> getActiveEventDefinitions(TriggerType triggerType) {
        List<Event> events = triggerType != null
                ? eventRepository.findAllByTriggerCondition_TriggerTypeAndStatusOrderByIdAsc(triggerType, EventStatus.ACTIVE)
                : eventRepository.findAllByStatusOrderByIdAsc(EventStatus.ACTIVE);

        return events.stream()
                .map(EventDefinitionResponse::from)
                .toList();
    }

    public void deleteEvent(Long eventId) {
        eventRepository.deleteById(eventId);
    }

    private Event getEventEntity(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: %s".formatted(eventId)));
    }

    //paylaod 검증
    private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        return payload == null || payload.isEmpty()
                ? Map.of()
                : Map.copyOf(payload);
    }
}
