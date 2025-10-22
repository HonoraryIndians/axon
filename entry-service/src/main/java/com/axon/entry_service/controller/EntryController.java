package com.axon.entry_service.controller;

import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.Kafka_Producer;
import com.axon.messaging.EventType;
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
        // TODO: 추후 Spring Security를 통해 JWT 토큰에서 실제 userId를 추출해야 함
        long userId = Long.parseLong(userDetails.getUsername());

        long timestamp = Instant.now().toEpochMilli();

        KafkaProducerDto eventDto = new KafkaProducerDto(
                EventType.FIRST_COME_FIRST_SERVE,
                requestDto.getEventId(),
                userId,
                requestDto.getProductId(),
                timestamp
        );
        log.info("요청 확인 {}", eventDto);
        producer.KafkasendMessage(axon_topic, eventDto);

        return ResponseEntity.accepted().build();
    }
}
