package com.axon.entry_service.service;

import com.axon.entry_service.dto.CouponRequestDto;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponEntryService {
    private final CampaignActivityProducerService campaignActivityProducerService;

    public void publishCouponIssue(CouponRequestDto payload) {
        CampaignActivityKafkaProducerDto message = CampaignActivityKafkaProducerDto.builder()
                .userId(payload.userId())
                .campaignActivityId(payload.campaignActivityId())
                .campaignActivityType(payload.campaignActivityType())
                .productId(payload.productId())
                .timestamp(Instant.now().toEpochMilli())
                .build();
        campaignActivityProducerService.send(KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, message);
        log.info("Published coupon issue command for user {} activity {}", payload.userId(), payload.campaignActivityId());
    }
}
