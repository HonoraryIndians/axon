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
    private Boolean isRetry;  // 재결제 시나리오 여부

    public static PaymentConfirmationResponse success(String reservationToken) {
        return PaymentConfirmationResponse.builder()
                .reservationResult(ReservationResult.success(null))
                .reservationToken(reservationToken)
                .reason(null)
                .isRetry(false)  // 신규 예약
                .build();
    }

    public static PaymentConfirmationResponse successWithRetry(String reservationToken) {
        return PaymentConfirmationResponse.builder()
                .reservationResult(ReservationResult.success(null))
                .reservationToken(reservationToken)
                .reason(null)
                .isRetry(true)  // 재결제 시나리오
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
