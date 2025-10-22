package com.axon.core_service.service;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import com.axon.core_service.domain.dto.evententry.EventEntryPageResponse;
import com.axon.core_service.exception.EventNotFoundException;
import com.axon.core_service.repository.EventEntryRepository;
import com.axon.core_service.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventEntryQueryService {

    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;

    @Transactional(readOnly = true)
    public EventEntryPageResponse findEntries(Long eventId, @Nullable EventEntryStatus status, Pageable pageable) {
        // 1. 이벤트 존재 여부 확인
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId)); // TODO: Custom Exception으로 변경

        // 2. 상태 값에 따라 다른 Repository 메소드 호출
        Page<EventEntry> entries = (status == null)
                ? eventEntryRepository.findByEvent_Id(event.getId(), pageable)
                : eventEntryRepository.findByEvent_IdAndStatus(event.getId(), status, pageable);

        // 3. DTO로 변환하여 반환
        return EventEntryPageResponse.from(eventId, status, entries);
    }
}
