package com.axon.entry_service.dto;

import com.axon.messaging.CampaignActivityType;
import lombok.Data;

@Data
public class EntryRequestDto {
    private CampaignActivityType campaignActivityType;
    private Long campaignActivityId;
    private Long productId;
    private Integer quantity = 1; // Default to 1
}
