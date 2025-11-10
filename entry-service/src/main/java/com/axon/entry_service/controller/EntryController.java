package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.*;
import com.axon.entry_service.service.exception.FastValidationException;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.dto.validation.ValidationResponse;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/entries")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8080")
public class EntryController {
    private final CampaignActivityProducerService producer;
    private final EntryReservationService reservationService;
    private final CampaignActivityMetaService campaignActivityMetaService;
    private final CoreValidationService coreValidationService;
    private final FastValidationService fastValidationService;

    @PostMapping
    public ResponseEntity<?> createEntry(@RequestBody EntryRequestDto requestDto,
                                            @RequestHeader("Authorization") String token,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("요청 확인 {}", requestDto);
        long campaignActivityId = requestDto.getCampaignActivityId();
        long userId = Long.parseLong(userDetails.getUsername());
        Instant now = Instant.now();

        //redis 빠른 검증
        CampaignActivityMeta meta = campaignActivityMetaService.getMeta(campaignActivityId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if(meta.hasFastValidation()) {
            try {
                fastValidationService.fastValidation(userId, meta);
            } catch  (FastValidationException e) {
                log.info("{}번 사용자가 [빠른검증]: {} 조건에서 실패!", userId, e.getType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
        }

        // 무거운 검증
        if(meta.hasHeavyValidation()) {
            ValidationResponse response = coreValidationService.isEligible(token, requestDto.getCampaignActivityId());
            if (!response.isEligible()) {
                log.info("{} 사용자의 요청이 {}번 응모요청의 자격미달로 통과하지 못했습니다.",userDetails.getUsername(), requestDto.getCampaignActivityId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response.getErrorMessage());
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

        long timestamp = now.toEpochMilli();

        CampaignActivityType type = requestDto.getCampaignActivityType() != null
                ? requestDto.getCampaignActivityType()
                : CampaignActivityType.FIRST_COME_FIRST_SERVE; //TODO 프로덕션 단계에서는 수중

        CampaignActivityKafkaProducerDto eventDto = new CampaignActivityKafkaProducerDto(
                type,
                requestDto.getCampaignActivityId(),
                userId,
                requestDto.getProductId(),
                timestamp
        );
        log.info("요청 확인 {}", eventDto);
        producer.send(KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, eventDto);

        return ResponseEntity.accepted().build();
    }
}
