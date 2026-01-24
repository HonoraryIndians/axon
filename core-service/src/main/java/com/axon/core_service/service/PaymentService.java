package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.messaging.dto.payment.ReservationTokenPayload;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StringRedisTemplate redisTemplate; // Use StringRedisTemplate to get raw JSON
    private final ObjectMapper objectMapper;
    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryService campaignActivityEntryService;
    private final PaymentFailureLogService paymentFailureLogService;

    // TODO: 이 상수는 Entry Service와 공유되어야 함. 현재는 하드코딩.
    private static final String TOKEN_PREFIX = "RESERVATION_TOKEN:";

    /**
     * 결제 처리 (Core Service 직접 수행)
     * 역할: 1차 토큰 검증 -> DB 저장 (결제 완료 처리)
     */
    @Transactional
    public void processPayment(String token, Long requestUserId) {
        String redisKey = TOKEN_PREFIX + token;
        
        // 1. Redis 1차 토큰 조회 (Raw JSON String)
        String jsonPayload = redisTemplate.opsForValue().get(redisKey);
        
        if (jsonPayload == null) {
            log.warn("결제 실패: 유효하지 않거나 만료된 토큰입니다. token={}, userId={}", token, requestUserId);
            throw new IllegalArgumentException("유효하지 않거나 만료된 결제 토큰입니다.");
        }

        ReservationTokenPayload payload;
        try {
            // Manual Deserialization to ignore class type info in JSON
            payload = objectMapper.readValue(jsonPayload, ReservationTokenPayload.class);
        } catch (Exception e) {
             log.error("토큰 Payload 역직렬화 실패", e);
             throw new IllegalStateException("시스템 오류: 토큰 정보를 읽을 수 없습니다.");
        }

        try {
            // 2. 본인 확인
            if (!payload.getUserId().equals(requestUserId)) {
                log.warn("결제 실패: 토큰 소유자와 요청자가 다릅니다. tokenUser={}, requestUser={}", payload.getUserId(), requestUserId);
                throw new IllegalArgumentException("잘못된 접근입니다.");
            }

            // 3. 결제 수행 (DB 저장)
            executePayment(payload);
            
            // 4. 토큰 삭제 (중복 결제 방지 및 멱등성 보장)
            redisTemplate.delete(redisKey);
            log.debug("1차 토큰 삭제 완료: {}", redisKey);

        } catch (Exception e) {
            log.error("결제 처리 중 예외 발생 (DB 저장 실패): userId={}. 장애 대응 로그를 생성합니다.", payload.getUserId(), e);
            
            // 실패 로그 저장 (별도 트랜잭션)
            // 이 로그가 저장되면 스케줄러가 나중에 복구하므로, 사용자에게는 성공으로 응답해도 됨 (Eventual Consistency)
            paymentFailureLogService.logFailure(payload, e);
            
            log.info("장애 대응 로그 저장 완료. 사용자에게는 성공 응답을 반환합니다. userId={}", payload.getUserId());
            // throw e; // 예외를 던지지 않고 정상 종료 처리
        }
    }

    /**
     * 재시도 결제 (스케줄러용)
     * 이미 결제가 확인된 건에 대해 토큰 검증 없이 DB 저장을 재시도합니다.
     */
    @Transactional
    public void retryPayment(ReservationTokenPayload payload) {
        executePayment(payload);
    }

    private void executePayment(ReservationTokenPayload payload) {
        // 캠페인 활동 조회
        CampaignActivity activity = campaignActivityRepository.findById(payload.getCampaignActivityId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 캠페인 활동입니다."));

        // 결제 완료 처리 (DB 저장)
        CampaignActivityKafkaProducerDto entryDto = CampaignActivityKafkaProducerDto.builder()
                .userId(payload.getUserId())
                .campaignActivityId(payload.getCampaignActivityId())
                .productId(payload.getProductId())
                .campaignActivityType(payload.getCampaignActivityType())
                .quantity(payload.getQuantity() != null ? payload.getQuantity().longValue() : 1L)
                .timestamp(Instant.now().toEpochMilli())
                .build();

        campaignActivityEntryService.upsertEntry(activity, entryDto, CampaignActivityEntryStatus.APPROVED, true);
        
        log.info("결제 완료 및 DB 저장 성공: userId={}, activityId={}", payload.getUserId(), payload.getCampaignActivityId());
    }
}
