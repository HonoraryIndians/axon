package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.Event;
import com.axon.messaging.EventType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventResponse {
    private final Long id;
    private final Long campaignId;
    private final String name;
    private final Integer limitCount;
    private final EventStatus status;
    private final LocalDateTime start_date;
    private final LocalDateTime end_date;
    private final EventType eventType;
    private final LocalDateTime created_at;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .campaignId(event.getCampaignId())
                .name(event.getEventName())
                .limitCount(event.getLimitCount())
                .status(event.getEventStatus())
                .start_date(event.getStartDate())
                .end_date(event.getEndDate())
                .eventType(event.getEventType())
                .created_at(event.getCreatedAt())
                .build();
    }
}
