package com.axon.core_service.service;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import com.axon.core_service.repository.EventEntryRepository;
import com.axon.messaging.dto.KafkaProducerDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class EventEntryService {
   private final EventEntryRepository eventEntryRepository;

   public EventEntry upsertEntry(Event event,
                                 KafkaProducerDto dto,
                                 EventEntryStatus nextStatus,
                                 boolean processed) {
       Instant requestedAt = Optional.ofNullable(dto.getTimestamp())
               .map(Instant::ofEpochMilli)
               .orElseGet(Instant::now);

       EventEntry entry = Optional.ofNullable(
               eventEntryRepository.findByEvent_IdAndUserId(event.getId(), dto.getUserId())
       ).orElseGet(() -> EventEntry.create(
               event,
               dto.getUserId(),
               dto.getProductId(),
               requestedAt
       ));

       entry.updateProduct(dto.getProductId());
       entry.updateStatus(nextStatus);
       if (processed) {
           entry.markProcessedNow();
       }
       return eventEntryRepository.save(entry);
   }
}
