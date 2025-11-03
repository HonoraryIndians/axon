package com.axon.core_service.service;

import com.axon.core_service.service.strategy.CampaignStrategy;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CampaignActivityConsumerService {

    private final Map<CampaignActivityType, CampaignStrategy> strategies;

    public CampaignActivityConsumerService(List<CampaignStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(CampaignStrategy::getType, Function.identity()));
    }

    @KafkaListener(topics = KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, groupId = "axon-group")
    public void consume(CampaignActivityKafkaProducerDto message) {
        log.info("Consumed message: {}", message);

        CampaignActivityType type = message.getCampaignActivityType();
        CampaignStrategy strategy = strategies.get(type);

        if (strategy != null) {
            strategy.process(message);
        } else {
            log.warn("지원하지 않는 캠페인 활동 타입입니다: {}", type);
        }
    }
}
