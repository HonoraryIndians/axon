package com.axon.entry_service.dto.Payment;

import com.axon.messaging.CampaignActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PaymentApprovalPayload {
    private Long userId;
    private Long campaignActivityId;
    private Long productId;
    private CampaignActivityType campaignActivityType;
    private String reservationToken;  // 1차 토큰
}
