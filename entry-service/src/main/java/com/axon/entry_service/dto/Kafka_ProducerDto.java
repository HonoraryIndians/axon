package com.axon.entry_service.dto;

import com.axon.entry_service.Enum.CampaignType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Kafka_ProducerDto {
    CampaignType campaignType;
    private int eventId;
    private int userId;
    private int productId;
    private Long timestamp;

}
