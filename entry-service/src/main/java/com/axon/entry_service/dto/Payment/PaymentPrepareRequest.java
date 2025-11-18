package com.axon.entry_service.dto.Payment;

import lombok.Data;

@Data
public class PaymentPrepareRequest {
    private String reservationToken;
}
