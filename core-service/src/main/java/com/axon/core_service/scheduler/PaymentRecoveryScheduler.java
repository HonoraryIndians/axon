package com.axon.core_service.scheduler;

import com.axon.core_service.domain.payment.PaymentFailureLog;
import com.axon.core_service.domain.payment.PaymentFailureStatus;
import com.axon.core_service.repository.PaymentFailureLogRepository;
import com.axon.messaging.dto.payment.ReservationTokenPayload;
import com.axon.messaging.topic.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentFailureLogRepository failureLogRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRY_COUNT = 5;

    /**
     * 결제 실패 로그 복구 스케줄러
     * 1분마다 실행되어 실패한 결제 건을 Kafka 재시도 토픽으로 발행합니다.
     */
    @Scheduled(fixedDelay = 60000) // 1분 간격
    @Transactional
    public void recoverFailedPayments() {
        List<PaymentFailureLog> targetLogs = failureLogRepository
                .findTop10ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        PaymentFailureStatus.PENDING, LocalDateTime.now()
                );

        if (targetLogs.isEmpty()) {
            return;
        }

        log.info("결제 장애 복구 시작: {} 건 (Kafka 전송)", targetLogs.size());

        for (PaymentFailureLog failureLog : targetLogs) {
            try {
                // 1. 상태 변경 (처리 중)
                failureLog.processing();
                
                // 2. Payload 복원
                ReservationTokenPayload payload = objectMapper.readValue(
                        failureLog.getPayload(), ReservationTokenPayload.class
                );

                // 3. Kafka 전송 (재시도 큐)
                kafkaTemplate.send(KafkaTopics.PAYMENT_RETRY_TOPIC, payload);

                // 4. 성공 처리 (Kafka로 넘겼음)
                failureLog.resolve();
                log.info("결제 복구 요청 Kafka 전송 완료: logId={}, userId={}", failureLog.getId(), failureLog.getUserId());

            } catch (Exception e) {
                log.error("결제 복구(Kafka 전송) 실패: logId={}, error={}", failureLog.getId(), e.getMessage());
                
                // 5. 실패 처리 및 지수 백오프 적용
                failureLog.incrementRetryCount();
                
                if (failureLog.getRetryCount() >= MAX_RETRY_COUNT) {
                    failureLog.fail(); // 영구 실패
                    log.error("결제 복구 최종 실패 (최대 횟수 초과): logId={}", failureLog.getId());
                }
            }
        }
    }
}
