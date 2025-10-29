package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import com.axon.core_service.domain.dto.event.EventOccurrenceResponse;
import com.axon.core_service.service.EventOccurrenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/collect/events")
@RequiredArgsConstructor
public class EventCollectorController {

    private final EventOccurrenceService eventOccurrenceService;

    @PostMapping
    public ResponseEntity<EventOccurrenceResponse> collect(@RequestBody @Valid EventOccurrenceRequest request) {
        return ResponseEntity.ok(eventOccurrenceService.record(request));
    }
}
