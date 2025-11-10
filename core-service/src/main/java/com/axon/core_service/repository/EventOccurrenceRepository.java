package com.axon.core_service.repository;

import com.axon.core_service.domain.event.EventOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOccurrenceRepository extends JpaRepository<EventOccurrence, Long> {
}
