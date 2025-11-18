package com.axon.entry_service.dto.Payment;

import com.axon.entry_service.domain.ReservationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfirmationResponse {
    private ReservationResult reservationResult;
    private String reservationToken;
    private String reason;

    public static PaymentConfirmationResponse success(String reservationToken) {
        return PaymentConfirmationResponse.builder()
                .reservationResult(ReservationResult.success(null))
                .reservationToken(reservationToken)
                .reason(null)
                .build();
    }

    public static PaymentConfirmationResponse failure(ReservationResult reservationResult, String reason) {
        return PaymentConfirmationResponse.builder()
                .reservationResult(reservationResult)
                .reservationToken(null)
                .reason(reason)
                .build();
    }

}
