package com.axon.core_service.repository;

import com.axon.core_service.domain.payment.PaymentFailureLog;
import com.axon.core_service.domain.payment.PaymentFailureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentFailureLogRepository extends JpaRepository<PaymentFailureLog, Long> {

    // 재시도 대상 조회 (상태가 PENDING이고, 다음 재시도 시간이 현재보다 과거인 것)
    List<PaymentFailureLog> findTop10ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            PaymentFailureStatus status, LocalDateTime now
    );
}
