package com.axon.core_service.repository;

import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventEntryRepository extends JpaRepository<EventEntry, Long> {

    Optional<EventEntry> findByEvent_IdAndUserId(Long eventId, Long userId);

    // 페이지네이션을 위한 메소드 추가
    Page<EventEntry> findByEvent_Id(Long eventId, Pageable pageable);

    Page<EventEntry> findByEvent_IdAndStatus(Long eventId, EventEntryStatus status, Pageable pageable);
}
