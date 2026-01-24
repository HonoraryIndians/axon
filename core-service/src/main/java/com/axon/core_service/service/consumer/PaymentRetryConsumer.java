package com.axon.core_service.service.consumer;

import com.axon.core_service.service.PaymentFailureLogService;
import com.axon.core_service.service.PaymentService;
import com.axon.messaging.dto.payment.ReservationTokenPayload;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryConsumer {

    private final PaymentService paymentService;
    private final PaymentFailureLogService paymentFailureLogService;

    /**
     * 결제 재시도 메시지 소비
     * Kafka DLQ에서 메시지를 받아 재결제(DB 저장)를 수행합니다.
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_RETRY_TOPIC, groupId = "axon-payment-retry-group")
    public void consumeRetryMessage(ReservationTokenPayload payload) {
        log.info("Kafka 재시도 요청 수신: userId={}, campaignActivityId={}", payload.getUserId(), payload.getCampaignActivityId());

        try {
            // 토큰 검증 없이 강제 결제 수행
            paymentService.retryPayment(payload);
            log.info("Kafka 재시도 처리 성공: userId={}", payload.getUserId());

        } catch (Exception e) {
            log.error("Kafka 재시도 처리 실패 (DB 재적재): userId={}", payload.getUserId(), e);
            // 처리 실패 시 다시 DB 로그로 저장하여 다음 배치 때 재시도하도록 함 (무한 루프 주의)
            // Retry Count 증가 로직이 없으므로 무한 루프 위험이 있음.
            // 개선: Payload에 retryCount를 담아서 보내거나, 여기서 바로 DLQ로 보내야 함.
            
            // 안전 장치: 여기서는 로그만 남기고, 수동 처리를 유도하거나 Dead Letter Topic으로 보냄.
            // 일단은 DB에 다시 쌓아서 재시도를 유도하되, 모니터링이 필요함.
            paymentFailureLogService.logFailure(payload, e);
        }
    }
}
