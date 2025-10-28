package com.axon.core_service.service;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import com.axon.core_service.repository.EventEntryRepository;
import com.axon.core_service.repository.EventRepository;
import com.axon.messaging.dto.KafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class EventEntryService {
    private final EventEntryRepository eventEntryRepository;
    private final ProductService productService; // ProductService 주입

    public EventEntry upsertEntry(Event event,
                                  KafkaProducerDto dto,
                                  EventEntryStatus nextStatus,
                                  boolean processed) {
        Instant requestedAt = Optional.ofNullable(dto.getTimestamp())
                .map(Instant::ofEpochMilli)
                .orElseGet(Instant::now);

        EventEntry entry = eventEntryRepository.findByEvent_IdAndUserId(event.getId(), dto.getUserId())
                .orElseGet(() -> EventEntry.create(
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

        // 선착순 성공(APPROVED) 상태일 때만 재고 차감 로직 호출
        if (nextStatus == EventEntryStatus.APPROVED) {
            productService.decreaseStock(dto.getProductId());
        }

        return eventEntryRepository.save(entry);
    }
}
