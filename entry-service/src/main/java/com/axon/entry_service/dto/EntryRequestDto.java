package com.axon.entry_service.dto;

import com.axon.entry_service.Enum.CampaignType;
import lombok.Data;

@Data
public class EntryRequestDto {
    CampaignType campaignType;
    private int eventId;
    private int productId;
}
