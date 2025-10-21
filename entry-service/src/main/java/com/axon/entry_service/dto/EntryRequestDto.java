package com.axon.entry_service.dto;

import com.axon.messaging.EventType;
import lombok.Data;

@Data
public class EntryRequestDto {
    private EventType campaignType;
    private Long eventId;
    private Long productId;
}
