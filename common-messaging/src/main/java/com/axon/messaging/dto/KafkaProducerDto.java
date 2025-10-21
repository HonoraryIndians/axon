package com.axon.messaging.dto;

import com.axon.messaging.CampaignType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaProducerDto {
    private CampaignType campaignType;
    private Long eventId;
    private Long userId;
    private Long productId;
    private Long timestamp;
}
