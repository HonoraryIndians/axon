package com.axon.core_service.dto;

import com.axon.core_service.domain.evententry.EventEntry;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;

import java.util.List;

@Getter
@Builder
public class EventEntryPageResponse {
    private final Long eventId;
    private final EventEntryStatus statusFilter;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final List<EventEntrySummary> entries;

    public static EventEntryPageResponse from(
            Long eventId,
            @Nullable EventEntryStatus status,
            Page<EventEntry> page) {

        List<EventEntrySummary> items = page.getContent().stream()
                .map(EventEntrySummary::from)
                .toList();

        return EventEntryPageResponse.builder()
                .eventId(eventId)
                .statusFilter(status)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .entries(items)
                .build();
    }
}
