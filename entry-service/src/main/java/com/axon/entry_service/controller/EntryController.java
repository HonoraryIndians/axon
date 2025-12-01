package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.dto.Payment.PaymentConfirmationResponse;
import com.axon.entry_service.dto.Payment.ReservationTokenPayload;
import com.axon.entry_service.service.*;
import com.axon.entry_service.service.Payment.ReservationTokenService;
import com.axon.entry_service.service.exception.FastValidationException;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.validation.ValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/entry/api/v1/entries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // Same-origin via Ingress, wildcard for flexibility
public class EntryController {
    private final EntryReservationService reservationService;
    private final CampaignActivityMetaService campaignActivityMetaService;
    private final CoreValidationService coreValidationService;
    private final FastValidationService fastValidationService;
    private final ReservationTokenService reservationTokenService;

    /**
     * Processes an entry creation request: validates eligibility, attempts an
     * atomic reservation, and emits a campaign activity event.
     *
     * @param requestDto  the entry request containing campaignActivityId,
     *                    productId, and optional activityType
     * @param token       the raw "Authorization" header value used for heavy
     *                    eligibility validation
     * @param userDetails the authenticated principal whose username is parsed as
     *                    the numeric userId
     * @return a ResponseEntity with status:
     *         202 Accepted on successful reservation and event emission;
     *         404 Not Found if campaign metadata is missing;
     *         400 Bad Request for fast- or heavy-validation failures or when the
     *         activity is closed;
     *         409 Conflict when the entry is duplicated;
     *         410 Gone when the activity is sold out;
     *         500 Internal Server Error for unexpected reservation failures.
     */

    @PostMapping
    public ResponseEntity<?> createEntry(@RequestBody EntryRequestDto requestDto,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("요청 확인 {}", requestDto);
        long campaignActivityId = requestDto.getCampaignActivityId();
        long userId = Long.parseLong(userDetails.getUsername());
        Instant now = Instant.now();

        CampaignActivityMeta meta = campaignActivityMetaService.getMeta(campaignActivityId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 캠페인 활동 데이터 조작 방어용 검증
        if (meta.productId() != null && !meta.productId().equals(requestDto.getProductId())) {
            log.warn("요청 중에 상품 정보가 일치하지 않는 요청이 있습니다. Meta {} || Request {}", meta.productId(),
                    requestDto.getProductId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentConfirmationResponse.failure(ReservationResult.error(), "상품 정보가 일치하지 않습니다."));
        }

        CampaignActivityType requestedType = requestDto.getCampaignActivityType() != null
                ? requestDto.getCampaignActivityType()
                : CampaignActivityType.FIRST_COME_FIRST_SERVE; // TODO: Default값 바꾸기
        if (meta.campaignActivityType() != null && !meta.campaignActivityType().equals(requestedType)) {
            log.warn("요청 정보 중에 캠페인 타입이 다른 요청이 있습니다. Meta {} || Request {}", meta.campaignActivityType(), requestedType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentConfirmationResponse.failure(ReservationResult.error(), "캠페인 타입이 일치하지 않습니다."));
        }

        // 재결제 용 1차 토큰 검증
        String deterministicToken = reservationTokenService.generateDeterministicToken(userId, campaignActivityId);
        Optional<ReservationTokenPayload> existingToken = reservationTokenService
                .getPayloadFromToken(deterministicToken);

        if (existingToken.isPresent()) {
            // 기존 1차 토큰 존재 → 재결제 시나리오
            log.info("재결제 시나리오: 기존 1차 토큰 재사용, userId={}, campaignActivityId={}, token={}...", userId,
                    campaignActivityId, deterministicToken.substring(0, Math.min(10, deterministicToken.length())));

            // 검증 스킵, 기존 토큰 그대로 반환
            return ResponseEntity.ok(PaymentConfirmationResponse.success(deterministicToken));
        }

        // 빠른 검증
        if (meta.hasFastValidation()) {
            try {
                fastValidationService.fastValidation(userId, meta);
            } catch (FastValidationException e) {
                log.info("{}번 사용자가 [빠른검증]: {} 조건에서 실패!", userId, e.getType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(PaymentConfirmationResponse.failure(ReservationResult.error(), e.getMessage()));
            }
        }

        // 무거운 검증
        if (meta.hasHeavyValidation()) {
            ValidationResponse response = coreValidationService.isEligible(token, requestDto.getCampaignActivityId());
            if (!response.isEligible()) {
                log.info("{} 사용자의 요청이 {}번 응모요청의 자격미달로 통과하지 못했습니다.", userDetails.getUsername(),
                        requestDto.getCampaignActivityId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        PaymentConfirmationResponse.failure(ReservationResult.error(), response.getErrorMessage()));
            }
        }

        // 원자적 검증
        ReservationResult result = reservationService.reserve(campaignActivityId, userId, meta, now);

        if (result.status() == ReservationStatus.DUPLICATED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (result.status() == ReservationStatus.SOLD_OUT) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        if (result.status() == ReservationStatus.CLOSED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        ReservationTokenPayload tokenPayload = ReservationTokenPayload.builder()
                .userId(userId)
                .campaignActivityId(meta.id())
                .productId(meta.productId())
                .campaignActivityType(meta.campaignActivityType())
                .quantity(requestDto.getQuantity())
                .build();
        String reservationToken = reservationTokenService.issueToken(tokenPayload);
        return ResponseEntity.ok(PaymentConfirmationResponse.success(reservationToken));
    }
}