package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.CampaignActivityProducerService;
import com.axon.entry_service.service.CampaignActivityMetaService;
import com.axon.entry_service.service.CoreValidationService;
import com.axon.entry_service.service.EntryReservationService;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
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
        // 무거운 검증
        boolean isEligible = coreValidationService.isEligible(token, requestDto.getCampaignActivityId());
        if (!isEligible) {
            log.info("{} 사용자의 요청이 {}번 응모요청의 자격미달로 통과하지 못했습니다.",userDetails.getUsername(), requestDto.getCampaignActivityId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("자격조건이 미달입니다.");
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
