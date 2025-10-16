package com.axon.entry_service.dto;

import com.axon.messaging.CampaignType;
import lombok.Data;

@Data
public class EntryRequestDto {
    private CampaignType campaignType;
    private int eventId;
    private int productId;
}
