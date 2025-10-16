package com.axon.messaging.dto;

import com.axon.messaging.CampaignType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Kafka_ProducerDto {
    private CampaignType campaignType;
    private int eventId;
    private int userId;
    private int productId;
    private Long timestamp;
}
