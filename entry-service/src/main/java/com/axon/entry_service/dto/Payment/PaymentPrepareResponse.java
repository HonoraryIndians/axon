package com.axon.entry_service.dto.Payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentPrepareResponse {
    private boolean success;
    private String message;
    private String ApprovalToken;

    public static PaymentPrepareResponse success(String message, String approvalToken) {
        return PaymentPrepareResponse.builder()
                .success(true)
                .ApprovalToken(approvalToken)
                .message(message).build();
    }

    public static PaymentPrepareResponse failure(String message) {
        return PaymentPrepareResponse.builder()
                .success(false)
                .ApprovalToken(null)
                .message(message).build();
    }

}
