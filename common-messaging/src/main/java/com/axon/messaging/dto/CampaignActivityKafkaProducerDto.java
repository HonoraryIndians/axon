package com.axon.messaging.dto;

import com.axon.messaging.CampaignActivityType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignActivityKafkaProducerDto {
    private CampaignActivityType campaignActivityType;
    private Long campaignActivityId;
    private Long userId;
    private Long productId;
    private Long timestamp;

    public Instant occurredAt() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
