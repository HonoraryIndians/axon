package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.dashboard.CohortAnalysisResponse;
import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.purchase.PurchaseType;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohortAnalysisService 테스트")
class CohortAnalysisServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private CampaignActivityRepository campaignActivityRepository;

    @InjectMocks
    private CohortAnalysisService cohortAnalysisService;

    private CampaignActivity testActivity;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        testActivity = CampaignActivity.builder()
                .name("테스트 선착순 이벤트")
                .budget(BigDecimal.valueOf(5_000_000))
                .price(BigDecimal.valueOf(1_290_000))
                .quantity(100)
                .startDate(LocalDateTime.ofInstant(baseTime.minus(7, ChronoUnit.DAYS), ZoneId.systemDefault()))
                .endDate(LocalDateTime.ofInstant(baseTime.plus(30, ChronoUnit.DAYS), ZoneId.systemDefault()))
                .build();
    }

    @Test
    @DisplayName("정상적인 Cohort 분석 - 단일 고객, 단일 구매")
    void analyzeCohort_SingleCustomer_SinglePurchase() {
        // Given
        Long activityId = 1L;

        Purchase firstPurchase = createPurchase(5001L, 1L, activityId, baseTime, 1_290_000);

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(any(), any(), any()))
                .willReturn(List.of(firstPurchase));
        given(purchaseRepository.findByUserIdIn(anyList()))
                .willReturn(List.of(firstPurchase));

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, null, null
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCustomers()).isEqualTo(1L);
        assertThat(response.avgCAC()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(response.ltvCurrent()).isEqualByComparingTo(BigDecimal.valueOf(1_290_000));
        assertThat(response.repeatPurchaseRate()).isEqualTo(0.0); // 재구매 없음
        assertThat(response.avgPurchaseFrequency()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("정상적인 Cohort 분석 - 단일 고객, 재구매 포함")
    void analyzeCohort_SingleCustomer_WithRepeatPurchases() {
        // Given
        Long activityId = 1L;
        Long userId = 5001L;

        Purchase firstPurchase = createPurchase(userId, 1L, activityId, baseTime, 1_290_000);
        Purchase secondPurchase = createPurchase(userId, 1L, activityId,
                baseTime.plus(30, ChronoUnit.DAYS), 300_000);
        Purchase thirdPurchase = createPurchase(userId, 1L, activityId,
                baseTime.plus(90, ChronoUnit.DAYS), 800_000);

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(any(), any(), any()))
                .willReturn(List.of(firstPurchase));
        given(purchaseRepository.findByUserIdIn(anyList()))
                .willReturn(Arrays.asList(firstPurchase, secondPurchase, thirdPurchase));

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, null, null
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCustomers()).isEqualTo(1L);

        // LTV 계산 검증
        // 30일 LTV: 초기 + 30일 재구매 = 1,290,000 + 300,000 = 1,590,000
        assertThat(response.ltv30d()).isEqualByComparingTo(BigDecimal.valueOf(1_590_000));

        // 90일 LTV: 초기 + 30일 + 90일 = 1,290,000 + 300,000 + 800,000 = 2,390,000
        assertThat(response.ltv90d()).isEqualByComparingTo(BigDecimal.valueOf(2_390_000));

        // Current LTV: 모든 구매 합계 / 고객 수
        assertThat(response.ltvCurrent()).isEqualByComparingTo(BigDecimal.valueOf(2_390_000));

        // 재구매율: 100% (1명 중 1명 재구매)
        assertThat(response.repeatPurchaseRate()).isEqualTo(100.0);

        // 평균 구매 횟수: 3회
        assertThat(response.avgPurchaseFrequency()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("정상적인 Cohort 분석 - 다수 고객, 재구매율 계산")
    void analyzeCohort_MultipleCustomers_RepeatPurchaseRate() {
        // Given
        Long activityId = 1L;

        // 고객 1: 재구매 O (3회)
        Purchase user1_first = createPurchase(5001L, 1L, activityId, baseTime, 1_290_000);
        Purchase user1_second = createPurchase(5001L, 1L, activityId,
                baseTime.plus(30, ChronoUnit.DAYS), 300_000);
        Purchase user1_third = createPurchase(5001L, 1L, activityId,
                baseTime.plus(90, ChronoUnit.DAYS), 800_000);

        // 고객 2: 재구매 O (2회)
        Purchase user2_first = createPurchase(5002L, 1L, activityId, baseTime, 1_290_000);
        Purchase user2_second = createPurchase(5002L, 1L, activityId,
                baseTime.plus(60, ChronoUnit.DAYS), 500_000);

        // 고객 3: 재구매 X (1회)
        Purchase user3_first = createPurchase(5003L, 1L, activityId, baseTime, 1_290_000);

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(any(), any(), any()))
                .willReturn(Arrays.asList(user1_first, user2_first, user3_first));
        given(purchaseRepository.findByUserIdIn(anyList()))
                .willReturn(Arrays.asList(
                        user1_first, user1_second, user1_third,
                        user2_first, user2_second,
                        user3_first
                ));

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, null, null
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCustomers()).isEqualTo(3L);

        // CAC: 5,000,000 / 3 = 1,666,666.67
        assertThat(response.avgCAC()).isEqualByComparingTo(new BigDecimal("1666666.67"));

        // 재구매율: 2명 / 3명 = 66.67%
        assertThat(response.repeatPurchaseRate()).isCloseTo(66.67, within(0.1));

        // 평균 구매 횟수: 6회 / 3명 = 2.0회
        assertThat(response.avgPurchaseFrequency()).isEqualTo(2.0);

        // 평균 주문 금액: 총액 / 6회
        BigDecimal totalRevenue = BigDecimal.valueOf(
                1_290_000 + 300_000 + 800_000 + // user1
                1_290_000 + 500_000 +           // user2
                1_290_000                        // user3
        ); // = 5,470,000
        BigDecimal expectedAOV = totalRevenue.divide(BigDecimal.valueOf(6), 2, java.math.RoundingMode.HALF_UP);
        assertThat(response.avgOrderValue()).isEqualByComparingTo(expectedAOV);
    }

    @Test
    @DisplayName("LTV/CAC 비율 계산 검증")
    void analyzeCohort_LtvCacRatio_Calculation() {
        // Given
        Long activityId = 1L;
        Long userId = 5001L;

        Purchase firstPurchase = createPurchase(userId, 1L, activityId, baseTime, 1_290_000);
        Purchase secondPurchase = createPurchase(userId, 1L, activityId,
                baseTime.plus(365, ChronoUnit.DAYS), 1_500_000);

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(any(), any(), any()))
                .willReturn(List.of(firstPurchase));
        given(purchaseRepository.findByUserIdIn(anyList()))
                .willReturn(Arrays.asList(firstPurchase, secondPurchase));

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, null, null
        );

        // Then
        // CAC: 5,000,000
        // Current LTV: 2,790,000
        // LTV/CAC Ratio = 2,790,000 / 5,000,000 = 0.558

        assertThat(response.avgCAC()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(response.ltvCurrent()).isEqualByComparingTo(BigDecimal.valueOf(2_790_000));
        assertThat(response.ltvCacRatioCurrent()).isCloseTo(0.56, within(0.01));

        // 365일 LTV도 동일 (모든 구매가 365일 이내)
        assertThat(response.ltv365d()).isEqualByComparingTo(BigDecimal.valueOf(2_790_000));
        assertThat(response.ltvCacRatio365d()).isCloseTo(0.56, within(0.01));
    }

    @Test
    @DisplayName("빈 Cohort - 구매 데이터 없음")
    void analyzeCohort_EmptyCohort_NoPurchases() {
        // Given
        Long activityId = 1L;

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(any(), any(), any()))
                .willReturn(Collections.emptyList());

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, null, null
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCustomers()).isEqualTo(0L);
        assertThat(response.avgCAC()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.ltvCurrent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.repeatPurchaseRate()).isEqualTo(0.0);
        assertThat(response.avgPurchaseFrequency()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("예외 처리 - Activity 없음")
    void analyzeCohort_ActivityNotFound_ThrowsException() {
        // Given
        Long activityId = 999L;

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                cohortAnalysisService.analyzeCohortByActivity(activityId, null, null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activity not found");
    }

    @Test
    @DisplayName("커스텀 기간으로 Cohort 분석")
    void analyzeCohort_WithCustomDateRange() {
        // Given
        Long activityId = 1L;
        LocalDateTime customStart = LocalDateTime.now().minusDays(30);
        LocalDateTime customEnd = LocalDateTime.now();

        Purchase purchase = createPurchase(5001L, 1L, activityId, baseTime, 1_290_000);

        given(campaignActivityRepository.findById(activityId))
                .willReturn(Optional.of(testActivity));
        given(purchaseRepository.findFirstPurchasesByActivityAndPeriod(
                eq(activityId),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(List.of(purchase));
        given(purchaseRepository.findByUserIdIn(anyList()))
                .willReturn(List.of(purchase));

        // When
        CohortAnalysisResponse response = cohortAnalysisService.analyzeCohortByActivity(
                activityId, customStart, customEnd
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.cohortStartDate()).isEqualToIgnoringSeconds(customStart);
        assertThat(response.cohortEndDate()).isEqualToIgnoringSeconds(customEnd);

        // Repository 메서드가 올바른 Instant 범위로 호출되었는지 검증
        then(purchaseRepository).should().findFirstPurchasesByActivityAndPeriod(
                eq(activityId),
                argThat(instant -> instant != null),
                argThat(instant -> instant != null)
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helper Methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Purchase createPurchase(Long userId, Long productId, Long activityId,
                                    Instant purchaseAt, long price) {
        return Purchase.builder()
                .userId(userId)
                .productId(productId)
                .campaignActivityId(activityId)
                .purchaseType(PurchaseType.CAMPAIGNACTIVITY)
                .price(BigDecimal.valueOf(price))
                .quantity(1)
                .purchasedAt(purchaseAt)
                .build();
    }
}
