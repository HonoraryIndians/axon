package com.axon.core_service.repository;

import com.axon.core_service.domain.evententry.EventEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEntryRepository extends JpaRepository<EventEntry, Long> {
    EventEntry findByEvent_IdAndUserId(Long eventId, Long userId);
}
