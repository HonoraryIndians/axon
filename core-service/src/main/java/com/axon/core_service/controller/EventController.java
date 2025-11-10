package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.event.EventDefinitionResponse;
import com.axon.core_service.domain.dto.event.EventRequest;
import com.axon.core_service.domain.dto.event.EventResponse;
import com.axon.core_service.domain.event.TriggerType;
import com.axon.core_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {
    private final EventService eventService;

    /**
     * Create a new event from the provided request.
     *
     * @param eventRequest the validated payload containing event details to create
     * @return the created EventResponse representing the new event
     */
    @PostMapping
    //생성
    public ResponseEntity<EventResponse> createEvent(@RequestBody @Valid EventRequest eventRequest) {
        log.info("이벤트 생성 요청: {}", eventRequest);
        return ResponseEntity.ok(eventService.createEvent(eventRequest));
    }

    /**
     * Retrieve all events.
     *
     * @return a list of EventResponse objects representing all events
     */
    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents() {
        return ResponseEntity.ok(eventService.getEvents());
    }

    /**
     * Retrieve active event definitions, optionally filtered by trigger type.
     *
     * @param triggerType optional trigger type to filter active event definitions; if null, all active definitions are returned
     * @return a list of active EventDefinitionResponse objects (filtered by the provided trigger type when present)
     */
    @GetMapping("/active")
    public ResponseEntity<List<EventDefinitionResponse>> getActiveEvents(
            @RequestParam(value = "triggerType", required = false) TriggerType triggerType) {
        return ResponseEntity.ok(eventService.getActiveEventDefinitions(triggerType));
    }
    /**
     * Retrieve a single event by its identifier.
     *
     * @param id the ID of the event to retrieve
     * @return the EventResponse for the specified event
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }
    /**
     * Update an existing event identified by its ID.
     *
     * @param id           the ID of the event to update
     * @param eventRequest the updated event data
     * @return the updated EventResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id,
                                                        @RequestBody @Valid EventRequest eventRequest) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventRequest));
    }
    /**
     * Delete the event identified by the given id.
     *
     * @param id the identifier of the event to delete
     * @return a ResponseEntity with HTTP status 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }



}