package com.axon.messaging.dto.payment;

import com.axon.messaging.CampaignActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationTokenPayload implements Serializable {
    private Long userId;
    private Long campaignActivityId;
    private Long productId;
    private CampaignActivityType campaignActivityType;
    private Integer quantity;
}
