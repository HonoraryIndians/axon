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

    /**
     * Creates a CampaignActivityConsumerService and builds an unmodifiable map from each strategy's type to the strategy.
     *
     * @param strategyList list of CampaignStrategy instances used to populate the internal unmodifiable map keyed by each strategy's type
     */
    public CampaignActivityConsumerService(List<CampaignStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(CampaignStrategy::getType, Function.identity()));
    }

    /**
     * Handles an incoming campaign activity message from the CAMPAIGN_ACTIVITY_COMMAND topic and delegates processing to the matching CampaignStrategy.
     *
     * If a strategy for the message's CampaignActivityType exists, the strategy's processing is invoked; otherwise a warning is logged. The consumed message is also logged.
     *
     * @param message the incoming campaign activity message to process
     */
    @KafkaListener(topics = KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND, groupId = "axon-group")
    public void consume(CampaignActivityKafkaProducerDto message) {

        CampaignActivityType type = message.getCampaignActivityType();
        CampaignStrategy strategy = strategies.get(type);

        if (strategy != null) {
            strategy.process(message);
        } else {
            log.warn("지원하지 않는 캠페인 활동 타입입니다: {}", type);
        }
        log.info("Consumed message: {}", message);
    }
}