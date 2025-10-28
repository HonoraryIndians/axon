package com.axon.entry_service.controller;

import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.Kafka_Producer;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.KafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final Kafka_Producer producer;
    private final String axon_topic = "event";

    @PostMapping
    public ResponseEntity<Void> createEntry(@RequestBody EntryRequestDto requestDto,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("요청 확인 {}", requestDto);
        long userId = Long.parseLong(userDetails.getUsername());

        long timestamp = Instant.now().toEpochMilli();

        CampaignActivityType type = requestDto.getCampaignActivityType() != null
                ? requestDto.getCampaignActivityType()
                : CampaignActivityType.FIRST_COME_FIRST_SERVE;

        KafkaProducerDto eventDto = new KafkaProducerDto(
                type,
                requestDto.getCampaignActivityId(),
                userId,
                requestDto.getProductId(),
                timestamp
        );
        log.info("요청 확인 {}", eventDto);
        producer.KafkasendMessage(axon_topic, eventDto);

        return ResponseEntity.accepted().build();
    }
}
