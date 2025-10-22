package com.axon.core_service.controller;

import com.axon.core_service.domain.evententry.EventEntryStatus;
import com.axon.core_service.domain.dto.evententry.EventEntryPageResponse;
import com.axon.core_service.service.EventEntryQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class EventEntryQueryController {

    private final EventEntryQueryService eventEntryQueryService;

    @GetMapping("/{eventId}/entries")
    public ResponseEntity<EventEntryPageResponse> getEventEntries(
            @PathVariable Long eventId,
            @RequestParam(required = false) EventEntryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("이벤트 참가자 조회 요청: eventId={}, status={}, page={}, size={}", eventId, status, page, size);

        // 페이지 번호와 사이즈 값 보정
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100); // 최소 1, 최대 100

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));

        EventEntryPageResponse response = eventEntryQueryService.findEntries(eventId, status, pageable);

        return ResponseEntity.ok(response);
    }
}
