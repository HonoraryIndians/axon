package com.axon.core_service.dto;

import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class EventEntrySummary {
    private final Long entryId;
    private final Long userId;
    private final Long productId;
    private final EventEntryStatus status;
    private final Instant requestedAt;
    private final Instant processedAt;
    private final String info;

    public static EventEntrySummary from(EventEntry entry) {
        return EventEntrySummary.builder()
                .entryId(entry.getId())
                .userId(entry.getUserId())
                .productId(entry.getProductId())
                .status(entry.getStatus())
                .requestedAt(entry.getRequestedAt())
                .processedAt(entry.getProcessedAt())
                .info(entry.getAdditionalData()) // 예시: additionalData를 info로 매핑
                .build();
    }
}
