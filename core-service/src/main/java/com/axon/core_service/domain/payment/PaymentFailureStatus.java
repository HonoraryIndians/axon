package com.axon.core_service.domain.payment;

public enum PaymentFailureStatus {
    PENDING,    // 재시도 대기 중
    PROCESSING, // 재시도 처리 중 (중복 실행 방지)
    RESOLVED,   // 재시도 성공 (복구됨)
    FAILED      // 최대 재시도 횟수 초과 (영구 실패 -> 관리자 개입 필요)
}
