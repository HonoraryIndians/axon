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

    /**
     * Create a new Event from the provided request and persist it.
     *
     * @param request the DTO containing event properties used to build the Event (name, description, status,
     *                triggerType and triggerPayload)
     * @return the persisted Event converted to an EventResponse
     */
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

    /**
     * Update an existing Event's name, description, trigger condition, and optionally its status.
     *
     * @param eventId the identifier of the Event to update
     * @param request payload containing the new name, description, trigger type and payload, and optional status
     * @return an EventResponse representing the updated Event
     * @throws IllegalArgumentException if no Event exists with the given id
     */
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

    /**
     * Update an event's name and description.
     *
     * @param eventId     the identifier of the event to update
     * @param name        the new name for the event
     * @param description the new description for the event
     * @return            an EventResponse reflecting the updated event
     * @throws IllegalArgumentException if no event exists with the given id
     */
    public EventResponse updateEventDetails(Long eventId, String name, String description) {
        Event event = getEventEntity(eventId);
        event.updateDetails(name, description);
        return EventResponse.from(event);
    }

    /**
     * Change the status of an existing Event and return its updated representation.
     *
     * @param eventId the identifier of the event to update
     * @param status the new status to apply to the event
     * @return an EventResponse representing the event after the status change
     * @throws IllegalArgumentException if no event exists with the given {@code eventId}
     */
    public EventResponse changeStatus(Long eventId, EventStatus status) {
        Event event = getEventEntity(eventId);
        event.changeStatus(status);
        return EventResponse.from(event);
    }

    /**
     * Retrieve an event by its identifier and return it as an EventResponse.
     *
     * @param eventId the identifier of the event to retrieve
     * @return the EventResponse representing the requested event
     * @throws IllegalArgumentException if no event exists with the given id
     */
    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return EventResponse.from(getEventEntity(eventId));
    }

    /**
     * Retrieve all events and convert them to response DTOs.
     *
     * @return a list of EventResponse objects representing all stored events
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getEvents() {
        return eventRepository.findAll().stream()
                .map(EventResponse::from)
                .toList();
    }

    /**
     * Retrieve active event definitions, optionally filtering by trigger type.
     *
     * @param triggerType the trigger type to filter by, or {@code null} to include all active events
     * @return a list of event definition responses for events with status ACTIVE, ordered by id ascending
     */
    @Transactional(readOnly = true)
    public List<EventDefinitionResponse> getActiveEventDefinitions(TriggerType triggerType) {
        List<Event> events = triggerType != null
                ? eventRepository.findAllByTriggerCondition_TriggerTypeAndStatusOrderByIdAsc(triggerType, EventStatus.ACTIVE)
                : eventRepository.findAllByStatusOrderByIdAsc(EventStatus.ACTIVE);

        return events.stream()
                .map(EventDefinitionResponse::from)
                .toList();
    }

    /**
     * Delete the Event with the given identifier.
     *
     * @param eventId the identifier of the Event to delete
     */
    public void deleteEvent(Long eventId) {
        eventRepository.deleteById(eventId);
    }

    /**
     * Retrieve the Event for the given id or throw an exception if no matching entity exists.
     *
     * @param eventId the identifier of the Event to retrieve
     * @return the matching Event entity
     * @throws IllegalArgumentException if no Event exists with the given id
     */
    private Event getEventEntity(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: %s".formatted(eventId)));
    }

    /**
     * Validate and produce an immutable payload map for event trigger data.
     *
     * @param payload the input payload map which may be null or empty
     * @return an immutable empty map if `payload` is null or empty, otherwise an immutable shallow copy of `payload`
     */
    private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        return payload == null || payload.isEmpty()
                ? Map.of()
                : Map.copyOf(payload);
    }
}