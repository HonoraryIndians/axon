package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.EventOccurrence;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventOccurrenceResponse {

    private final Long id;
    private final Long eventId;
    private final LocalDateTime occurredAt;
    private final Long userId;
    private final String pageUrl;

    public static EventOccurrenceResponse from(EventOccurrence occurrence) {
        return EventOccurrenceResponse.builder()
                .id(occurrence.getId())
                .eventId(occurrence.getEvent().getId())
                .occurredAt(occurrence.getOccurredAt())
                .userId(occurrence.getUserId())
                .pageUrl(occurrence.getPageUrl())
                .build();
    }
}
