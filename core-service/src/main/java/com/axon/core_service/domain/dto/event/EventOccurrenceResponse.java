package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.EventOccurrence;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventOccurrenceResponse {
    private final Long eventId;
    private final LocalDateTime occurredAt;
    private final Long userId;

    public static EventOccurrenceResponse from(EventOccurrenceRequest occurrence) {
        return EventOccurrenceResponse.builder()
                .eventId(occurrence.getEventId())
                .occurredAt(occurrence.getOccurredAt())
                .userId(occurrence.getUserId())
                .build();
    }
}
