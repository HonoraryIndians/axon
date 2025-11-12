package com.axon.entry_service.controller;

import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.dto.Payment.PaymentConfirmationRequest;
import com.axon.entry_service.dto.Payment.PaymentConfirmationResponse;
import com.axon.entry_service.service.CampaignActivityProducerService;
import com.axon.entry_service.service.Payment.ReservationTokenService;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final ReservationTokenService reservationTokenService;
    private final CampaignActivityProducerService campaignActivityProducerService;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmationResponse> confirmPayment(@AuthenticationPrincipal UserDetails userDetails, @RequestBody PaymentConfirmationRequest paymentConfirmationRequest) {
        String reservationToken = paymentConfirmationRequest.getReservationToken();
        long currentUserId = Long.parseLong(userDetails.getUsername());
        long timestamp = Instant.now().toEpochMilli();

        return reservationTokenService.getPayloadFromToken(reservationToken)
                .map(payload -> {

                    if(!payload.getUserId().equals(currentUserId)) {
                        log.warn("Token theft suspected! Token owner: {}, Requester: {}", payload.getUserId(), currentUserId);
                        return new ResponseEntity<>(PaymentConfirmationResponse.failure(ReservationResult.error(), "응모자와 다른 요청입니다."), HttpStatus.FORBIDDEN);
                    }

                    // Redis의 신뢰할 수 있는 payload로 Kafka DTO 생성 (데이터 위변조 방어)
                    CampaignActivityKafkaProducerDto kafkaProducerDto = CampaignActivityKafkaProducerDto.builder()
                            .campaignActivityType(payload.getCampaignActivityType())
                            .campaignActivityId(payload.getCampaignActivityId())
                            .userId(payload.getUserId())
                            .productId(payload.getProductId())
                            .timestamp(timestamp).build();
                    log.info("요청 확인 {}", kafkaProducerDto);
                    campaignActivityProducerService.send(KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, kafkaProducerDto);

                    reservationTokenService.removeToken(reservationToken);
                    log.info("결제 완료, 결제 토큰 삭제: {}", reservationToken);
                    return new ResponseEntity<>(PaymentConfirmationResponse.success(reservationToken), HttpStatus.OK);
                }).orElseGet(() -> {
                    log.warn("결제 토큰이 유효하지 않거나 존재하지 않습니다. Token: {}", reservationToken);
                    return new ResponseEntity<>(
                            PaymentConfirmationResponse.failure(ReservationResult.error(), "결제 오류"), HttpStatus.BAD_REQUEST
                    );
                });
    }
}
