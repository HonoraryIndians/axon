package com.axon.entry_service.controller;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.EntryReservationService;
import com.axon.messaging.CampaignActivityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Test-only controller for reservation endpoints.
 * This controller is ONLY active in non-production profiles and will be
 * completely
 * excluded from production builds.
 * 
 * Use this for testing scripts and development without compromising production
 * security.
 */
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestReservationController {

        private final EntryReservationService reservationService;
        private final com.axon.entry_service.service.CampaignActivityProducerService producerService;

        /**
         * Test-only reservation endpoint that bypasses authentication and uses mock
         * metadata.
         * This endpoint is completely independent from core-service and uses hardcoded
         * test data.
         * Triggers the complete event flow: ReservationApprovedEvent →
         * BackendEventPublisher → Kafka → ES
         *
         * @param userId  user ID for the reservation
         * @param request reservation request containing campaignActivityId and
         *                productId
         * @return reservation result with success/failure status
         */
        @PostMapping("/reserve/{userId}")
        public ResponseEntity<ReservationResult> testReserve(
                        @PathVariable Long userId,
                        @RequestBody EntryRequestDto request) {
                log.info("Test reservation request: activityId={}, userId={}, productId={}",
                                request.getCampaignActivityId(), userId, request.getProductId());

                // Create mock metadata for testing (no core-service dependency)
                CampaignActivityMeta meta = new CampaignActivityMeta(
                                request.getCampaignActivityId(), // id
                                1000, // limitCount (high limit for testing)
                                CampaignActivityStatus.ACTIVE, // status
                                LocalDateTime.now().minusDays(1), // startDate
                                LocalDateTime.now().plusDays(30), // endDate
                                Collections.emptyList(), // filters
                                true, // hasFastValidation
                                false, // hasHeavyValidation
                                request.getProductId(), // productId
                                CampaignActivityType.FIRST_COME_FIRST_SERVE // campaignActivityType
                );

                ReservationResult result = reservationService.reserve(
                                request.getCampaignActivityId(),
                                userId,
                                meta,
                                Instant.now());

                log.info("Test reservation result: {}", result);

                if (result.isSuccess()) {
                        // For testing purposes, immediately trigger purchase creation in Core Service
                        // This simulates the "Payment -> Purchase" flow without actual payment
                        com.axon.messaging.dto.CampaignActivityKafkaProducerDto command = com.axon.messaging.dto.CampaignActivityKafkaProducerDto
                                        .builder()
                                        .campaignActivityId(request.getCampaignActivityId())
                                        .userId(userId)
                                        .productId(request.getProductId())
                                        .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                                        .timestamp(Instant.now().toEpochMilli())
                                        .quantity(1)
                                        .build();

                        producerService.send(com.axon.messaging.topic.KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, command);
                        log.info("Test: Triggered purchase creation for userId={} activityId={}", userId,
                                        request.getCampaignActivityId());
                }

                return ResponseEntity.ok(result);
        }
}
