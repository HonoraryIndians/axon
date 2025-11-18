package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.dto.Payment.PaymentApprovalPayload;
import com.axon.entry_service.dto.Payment.PaymentConfirmationRequest;
import com.axon.entry_service.dto.Payment.PaymentPrepareRequest;
import com.axon.entry_service.dto.Payment.ReservationTokenPayload;
import com.axon.entry_service.service.CampaignActivityMetaService;
import com.axon.entry_service.service.Payment.PaymentService;
import com.axon.entry_service.service.Payment.ReservationTokenService;
import com.axon.messaging.CampaignActivityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("결제 플로우 통합 테스트")
class PaymentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ReservationTokenService reservationTokenService;

    @MockitoBean
    private CampaignActivityMetaService campaignActivityMetaService;

    @MockitoBean
    private PaymentService paymentService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_CAMPAIGN_ACTIVITY_ID = 100L;
    private static final Long TEST_PRODUCT_ID = 200L;

    @BeforeEach
    void setUp() {
        redisTemplate.keys("*").forEach(key -> redisTemplate.delete(key));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(10);
        CampaignActivityMeta meta = new CampaignActivityMeta(
            TEST_CAMPAIGN_ACTIVITY_ID,
            100,
            CampaignActivityStatus.ACTIVE,
            startTime,
            endTime,
            null,
            false,
            false,
            TEST_PRODUCT_ID,
            CampaignActivityType.FIRST_COME_FIRST_SERVE
        );
        when(campaignActivityMetaService.getMeta(TEST_CAMPAIGN_ACTIVITY_ID)).thenReturn(meta);
    }

    @Test
    @DisplayName("시나리오 1: 정상 플로우")
    @WithMockUser(username = "1")
    void paymentFlow_Success() throws Exception {
        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
            .userId(TEST_USER_ID)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .build();

        String reservationToken = reservationTokenService.issueToken(tokenPayload);

        PaymentPrepareRequest prepareRequest = new PaymentPrepareRequest();
        prepareRequest.setReservationToken(reservationToken);

        ResultActions prepareActions = mockMvc.perform(post("/api/v1/payments/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareRequest)));

        prepareActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.approvalToken").exists());

        String approvalToken = objectMapper.readTree(prepareActions.andReturn().getResponse().getContentAsString())
                .get("approvalToken").asText();

        given(paymentService.sendToKafkaWithRetry(any(PaymentApprovalPayload.class), anyInt())).willReturn(true);

        PaymentConfirmationRequest confirmRequest = new PaymentConfirmationRequest();
        confirmRequest.setReservationToken(approvalToken);

        ResultActions confirmActions = mockMvc.perform(post("/api/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest)));

        confirmActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationResult.status").value("SUCCESS"))
                .andDo(print());

        verify(paymentService, times(1)).sendToKafkaWithRetry(any(PaymentApprovalPayload.class), eq(3));
    }

    @Test
    @DisplayName("시나리오 2: 1차 토큰 만료")
    @WithMockUser(username = "1")
    void preparePayment_Fail_When_ReservationToken_Expired() throws Exception {
        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
            .userId(TEST_USER_ID)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .build();

        String reservationToken = reservationTokenService.issueToken(tokenPayload);
        reservationTokenService.removeToken(reservationToken);

        PaymentPrepareRequest prepareRequest = new PaymentPrepareRequest();
        prepareRequest.setReservationToken(reservationToken);

        ResultActions prepareActions = mockMvc.perform(post("/api/v1/payments/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareRequest)));

        prepareActions.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("결제 시간이 만료되었습니다. 처음부터 다시 응모해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("시나리오 3: 토큰 탈취 (prepare)")
    @WithMockUser(username = "2")
    void preparePayment_Fail_When_Token_Stolen() throws Exception {
        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
            .userId(1L)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .build();

        String reservationToken = reservationTokenService.issueToken(tokenPayload);

        PaymentPrepareRequest prepareRequest = new PaymentPrepareRequest();
        prepareRequest.setReservationToken(reservationToken);

        ResultActions prepareActions = mockMvc.perform(post("/api/v1/payments/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareRequest)));

        prepareActions.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(print());
    }

    @Test
    @DisplayName("시나리오 4: Kafka 전송 실패")
    @WithMockUser(username = "1")
    void confirmPayment_Fail_When_Kafka_Send_Fails() throws Exception {
        PaymentApprovalPayload approvalPayload = PaymentApprovalPayload.builder()
            .userId(TEST_USER_ID)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .reservationToken("dummy-token")
            .build();

        String approvalToken = reservationTokenService.CreateApprovalToken(approvalPayload);

        given(paymentService.sendToKafkaWithRetry(any(PaymentApprovalPayload.class), anyInt())).willReturn(false);

        PaymentConfirmationRequest confirmRequest = new PaymentConfirmationRequest();
        confirmRequest.setReservationToken(approvalToken);

        ResultActions confirmActions = mockMvc.perform(post("/api/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest)));

        confirmActions.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reservationResult.status").value("ERROR"))
                .andExpect(jsonPath("$.reason").value("일시적인 오류로 결제가 취소되었습니다. 처음부터 다시 응모해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("시나리오 5: 재결제 - Entry에서 기존 1차 토큰 확인 후 검증 스킵")
    @WithMockUser(username = "1")
    void retryPayment_Entry_Skip_Validation_With_ExistingToken() throws Exception {
        // Given: 1차 응모로 1차 토큰 발급
        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
            .userId(TEST_USER_ID)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .build();

        String firstToken = reservationTokenService.issueToken(tokenPayload);

        // When: 재응모 (Entry 호출)
        com.axon.entry_service.dto.EntryRequestDto entryRequest = new com.axon.entry_service.dto.EntryRequestDto();
        entryRequest.setCampaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID);
        entryRequest.setProductId(TEST_PRODUCT_ID);
        entryRequest.setCampaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE);

        ResultActions entryActions = mockMvc.perform(post("/api/v1/entries")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entryRequest)));

        // Then: 기존 1차 토큰 재사용 (검증 스킵됨)
        entryActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationToken").value(firstToken))
                .andDo(print());

        // Verify: 동일한 토큰 반환
        String responseToken = objectMapper.readTree(entryActions.andReturn().getResponse().getContentAsString())
                .get("reservationToken").asText();

        assertThat(responseToken).isEqualTo(firstToken);
    }

    @Test
    @DisplayName("시나리오 6: 2차 토큰 TTL 갱신")
    @WithMockUser(username = "1")
    void retryPayment_ApprovalToken_TTL_Refresh() throws Exception {
        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
            .userId(TEST_USER_ID)
            .campaignActivityId(TEST_CAMPAIGN_ACTIVITY_ID)
            .productId(TEST_PRODUCT_ID)
            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
            .build();

        String reservationToken = reservationTokenService.issueToken(tokenPayload);

        PaymentPrepareRequest prepareRequest1 = new PaymentPrepareRequest();
        prepareRequest1.setReservationToken(reservationToken);

        mockMvc.perform(post("/api/v1/payments/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareRequest1)))
            .andExpect(status().isOk());

        PaymentPrepareRequest prepareRequest2 = new PaymentPrepareRequest();
        prepareRequest2.setReservationToken(reservationToken);

        ResultActions prepareActions = mockMvc.perform(post("/api/v1/payments/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareRequest2)));

        prepareActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(print());

        String approvalToken = objectMapper.readTree(prepareActions.andReturn().getResponse().getContentAsString())
                .get("approvalToken").asText();

        Optional<PaymentApprovalPayload> payload = reservationTokenService.getApprovalPayload(approvalToken);
        assertThat(payload).isPresent();
    }

    @Test
    @DisplayName("시나리오 7: 결정론적 토큰 - 같은 사용자 같은 토큰")
    void deterministicToken_SameUserSameToken() {
        String token1 = reservationTokenService.generateDeterministicToken(TEST_USER_ID, TEST_CAMPAIGN_ACTIVITY_ID);
        String token2 = reservationTokenService.generateDeterministicToken(TEST_USER_ID, TEST_CAMPAIGN_ACTIVITY_ID);

        assertThat(token1).isEqualTo(token2);
    }

    @Test
    @DisplayName("시나리오 8: 결정론적 토큰 - 다른 사용자 다른 토큰")
    void deterministicToken_DifferentUserDifferentToken() {
        String token1 = reservationTokenService.generateDeterministicToken(1L, TEST_CAMPAIGN_ACTIVITY_ID);
        String token2 = reservationTokenService.generateDeterministicToken(2L, TEST_CAMPAIGN_ACTIVITY_ID);

        assertThat(token1).isNotEqualTo(token2);
    }
}
