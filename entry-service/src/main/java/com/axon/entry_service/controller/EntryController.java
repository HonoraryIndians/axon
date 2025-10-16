package com.axon.entry_service.controller;

import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.Kafka_Producer;
import com.axon.messaging.dto.Kafka_ProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/entries")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8080") // core-service의 frontendからのリクエストを許可
@Slf4j
public class EntryController {
    private final Kafka_Producer producer;
    private final String axonTopic = "AXON-topic";

    @PostMapping
    public ResponseEntity<Void> createEntry(@RequestBody EntryRequestDto requestDto) {
        // TODO: 추후 Spring Security를 통해 JWT 토큰에서 실제 userId를 추출해야 함
        int userId = 1; // 임시로 하드코딩

        long timestamp = Instant.now().toEpochMilli();

        Kafka_ProducerDto eventDto = new Kafka_ProducerDto(
                requestDto.getCampaignType(),
                requestDto.getEventId(),
                userId,
                requestDto.getProductId(),
                timestamp
        );

        producer.KafkasendMessage(axonTopic, eventDto);
        log.info("Produced event: {}", eventDto);
        return ResponseEntity.accepted().build();
    }
}
