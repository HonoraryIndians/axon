package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.CampaignActivityProducerService;
import com.axon.entry_service.service.CampaignActivityMetaService;
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

    @PostMapping
    public ResponseEntity<Void> createEntry(@RequestBody EntryRequestDto requestDto,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("요청 확인 {}", requestDto);
        long campaignActivityId = requestDto.getCampaignActivityId();
        long userId = Long.parseLong(userDetails.getUsername());
        Instant now = Instant.now();

        CampaignActivityMeta meta = campaignActivityMetaService.getMeta(campaignActivityId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

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
