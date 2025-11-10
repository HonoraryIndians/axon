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

    @PostMapping
    //생성
    public ResponseEntity<EventResponse> createEvent(@RequestBody @Valid EventRequest eventRequest) {
        log.info("이벤트 생성 요청: {}", eventRequest);
        return ResponseEntity.ok(eventService.createEvent(eventRequest));
    }

    //전체 조회
    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents() {
        return ResponseEntity.ok(eventService.getEvents());
    }

    @GetMapping("/active")
    public ResponseEntity<List<EventDefinitionResponse>> getActiveEvents(
            @RequestParam(value = "triggerType", required = false) TriggerType triggerType) {
        return ResponseEntity.ok(eventService.getActiveEventDefinitions(triggerType));
    }
    //하나 조회
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }
    //수정
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id,
                                                        @RequestBody @Valid EventRequest eventRequest) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventRequest));
    }
    //제거
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }



}
