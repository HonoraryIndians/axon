package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.domain.payment.PaymentFailureLog;
import com.axon.core_service.domain.payment.PaymentFailureStatus;
import com.axon.core_service.repository.PaymentFailureLogRepository;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.payment.ReservationTokenPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.axon.core_service.scheduler.PaymentRecoveryScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.repository.CampaignActivityRepository;
import org.junit.jupiter.api.BeforeEach;

// ... imports ...

@SpringBootTest(properties = {
        "axon.entry-service-url=http://localhost:8081",
        "spring.kafka.consumer.group-id=test-group"
})
public class PaymentResilienceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentFailureLogRepository failureLogRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignActivityRepository campaignActivityRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PaymentRecoveryScheduler recoveryScheduler;

    @MockitoBean
    private CampaignActivityEntryService campaignActivityEntryService;

    @MockitoBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    private Long activityId;

    @BeforeEach
    void setUp() {
        // 캠페인 생성
        Campaign campaign = Campaign.builder()
                .name("Test Campaign")
                .build();
        campaign = campaignRepository.save(campaign);

        // 캠페인 활동 데이터 생성 및 저장
        CampaignActivity activity = CampaignActivity.builder()
                .campaign(campaign)
                .name("Resilience Test Activity")
                .activityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                .status(CampaignActivityStatus.ACTIVE)
                .price(java.math.BigDecimal.valueOf(10000))
                .quantity(100)
                .startDate(java.time.LocalDateTime.now())
                .endDate(java.time.LocalDateTime.now().plusDays(7))
                .build();
        activity = campaignActivityRepository.save(activity);
        this.activityId = activity.getId();
    }

    @Test
    @DisplayName("DB 저장 실패 시 로그 적재 및 스케줄러 복구 테스트")
    void testPaymentFailureLoggingAndRecovery() {
        // Given: 1차 토큰 생성 및 Redis 저장
        String token = "test-resilience-token";
        Long userId = 9999L;
        ReservationTokenPayload payload = ReservationTokenPayload.builder()
                .userId(userId)
                .campaignActivityId(activityId)
                .productId(1L)
                .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                .quantity(1)
                .build();
        
        String redisKey = "RESERVATION_TOKEN:" + token;
        redisTemplate.opsForValue().set(redisKey, payload, 5, TimeUnit.MINUTES);

        // 1. 강제로 DB 저장 시점에 에러 발생 설정
        doThrow(new RuntimeException("CRITICAL_DB_ERROR_SIMULATION"))
                .when(campaignActivityEntryService)
                .upsertEntry(any(), any(), eq(CampaignActivityEntryStatus.APPROVED), eq(true));

        // When: 결제 시도 (Exception Swallow -> Success)
        try {
            paymentService.processPayment(token, userId);
        } catch (Exception e) {
            throw new AssertionError("Exception should have been swallowed by PaymentService", e);
        }

        // Then 1: 실패 로그가 PENDING 상태로 쌓여있어야 함
        List<PaymentFailureLog> logs = failureLogRepository.findAll();
        PaymentFailureLog lastLog = logs.stream()
                .filter(l -> l.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PaymentFailureLog not found for user: " + userId));

        assertThat(lastLog.getStatus()).isEqualTo(PaymentFailureStatus.PENDING);
        assertThat(lastLog.getErrorMessage()).contains("CRITICAL_DB_ERROR_SIMULATION");

        // When 2: 스케줄러 수동 실행 (자동 복구 시도)
        recoveryScheduler.recoverFailedPayments();

        // Then 2: 로그 상태가 RESOLVED로 변경되어야 함
        // (주의: Kafka 전송 성공 시 RESOLVED가 됨)
        PaymentFailureLog updatedLog = failureLogRepository.findById(lastLog.getId()).orElseThrow();
        assertThat(updatedLog.getStatus()).isEqualTo(PaymentFailureStatus.RESOLVED);
        
        logCleanup(userId, redisKey);
    }

    private void logCleanup(Long userId, String redisKey) {
        redisTemplate.delete(redisKey);
        // Optional: delete the log entry to keep the DB clean for multiple test runs
        failureLogRepository.deleteAll();
    }
}
