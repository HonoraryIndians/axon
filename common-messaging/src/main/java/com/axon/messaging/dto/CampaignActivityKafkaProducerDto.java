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

    /**
     * Get the event time as an Instant.
     *
     * If {@code timestamp} is non-null, it is interpreted as epoch milliseconds and converted to an {@link Instant}; otherwise the current system instant is used.
     *
     * @return the event time as an {@link Instant}
     */
    public Instant occurredAt() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}