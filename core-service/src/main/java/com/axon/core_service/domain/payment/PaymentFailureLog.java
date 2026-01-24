package com.axon.core_service.domain.payment;

import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_failure_log", indexes = {
        @Index(name = "idx_status_next_retry", columnList = "status, nextRetryAt")
})
public class PaymentFailureLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON (ReservationTokenPayload)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentFailureStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Builder
    public PaymentFailureLog(Long userId, String payload, String errorMessage) {
        this.userId = userId;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.status = PaymentFailureStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now(); // 즉시 재시도 가능
    }

    public void incrementRetryCount() {
        this.retryCount++;
        // 지수 백오프: 1분 -> 2분 -> 4분 -> 8분 ...
        int backoffMinutes = (int) Math.pow(2, this.retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes);
        this.status = PaymentFailureStatus.PENDING; // 다시 대기 상태로
    }

    public void resolve() {
        this.status = PaymentFailureStatus.RESOLVED;
    }

    public void fail() {
        this.status = PaymentFailureStatus.FAILED;
    }
    
    public void processing() {
        this.status = PaymentFailureStatus.PROCESSING;
    }
}
