package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.campaign.CampaignStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventResponse {
    private final Integer id;
    private final Long campaignId;
    private final String name;
    private final Integer limitCount;
    private final CampaignStatus status;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .campaignId(event.getCampaignId())
                .name(event.getEventName())
                .limitCount(event.getLimitCount())
                .status(event.getCampaignStatus())
                .build();
    }
}
