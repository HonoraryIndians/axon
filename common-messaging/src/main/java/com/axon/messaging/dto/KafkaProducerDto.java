package com.axon.messaging.dto;

import com.axon.messaging.CampaignActivityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaProducerDto {
    private CampaignActivityType campaignActivityType;
    private Long campaignActivityId;
    private Long userId;
    private Long productId;
    private Long timestamp;
}
