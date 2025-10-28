package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.event.EventRequest;
import com.axon.core_service.domain.dto.event.EventResponse;
import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.core_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    // 캠페인 하위 이벤트를 등록하고 한도·상태를 설정한다.
    @PostMapping("/{campaignId}/events")
    public ResponseEntity<EventResponse> createEvent(
            @PathVariable Long campaignId,
            @RequestBody @Valid EventRequest request
    ) {
        return ResponseEntity.ok(eventService.createEvent(campaignId, request));
    }

    // 전체 이벤트의 개수를 조회한다.
    @GetMapping("/count")
    public ResponseEntity<Long> getTotalEventCount() {
        return ResponseEntity.ok(eventService.getTotalEventCount());
    }

    // 단일 이벤트의 상세 정보를 조회한다.
    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }

    // 이벤트의 기본 정보를 수정한다.
    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long eventId,
            @RequestBody @Valid EventRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEvent(eventId, request));
    }

    // 이벤트 상태만 변경한다.
    @PatchMapping("/{eventId}/status")
    public ResponseEntity<EventResponse> changeEventStatus(
            @PathVariable Long eventId,
            @RequestParam EventStatus status
    ) {
        return ResponseEntity.ok(eventService.changeEventStatus(eventId, status));
    }

    // 이벤트를 삭제한다.
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        eventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    // 전체 이벤트를 조회한다.
    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    // 특정 캠페인에 속한 이벤트 목록을 조회한다.
    @GetMapping("/{campaignId}/events")
    public ResponseEntity<List<EventResponse>> getEvents(@PathVariable Long campaignId) {
        return ResponseEntity.ok(eventService.getEvents(campaignId));
    }
}
