package com.axon.entry_service.service.Payment;
import com.axon.entry_service.dto.Payment.PaymentApprovalPayload;
import com.axon.entry_service.service.CampaignActivityProducerService;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final CampaignActivityProducerService campaignActivityProducerService;

    // 카프카 전송 retry
    public boolean sendToKafkaWithRetry(PaymentApprovalPayload payload, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // DTO 변환
                CampaignActivityKafkaProducerDto message = CampaignActivityKafkaProducerDto.builder()
                        .userId(payload.getUserId())
                        .campaignActivityId(payload.getCampaignActivityId())
                        .productId(payload.getProductId())
                        .campaignActivityType(payload.getCampaignActivityType())
                        .timestamp(Instant.now().toEpochMilli())
                        .build();

                // Kafka 전송
                campaignActivityProducerService.send(KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, message);
                log.info("Kafka 전송 성공 (attempt {}): userId={}, campaignActivityId={}", attempt, payload.getUserId(), payload.getCampaignActivityId());
                return true;

            } catch (Exception e) {
                log.error("Kafka 전송 실패 (attempt {}/{}): userId={}, error={}",attempt, maxRetries, payload.getUserId(), e.getMessage());

        // 마지막 시도가 아니면 대기 후 재시도
        if (attempt < maxRetries) {
            try {
                // 지수 백오프: 1초, 2초, 3초
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("재시도 대기 중 인터럽트 발생", ie);
                return false;
            }
        }
    }
}

        // 3회 모두 실패
        log.error("Kafka 전송 최종 실패: userId={}, campaignActivityId={}", payload.getUserId(), payload.getCampaignActivityId());
        return false;
    }

}
