package com.axon.core_service.service;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;
import com.axon.core_service.service.strategy.CampaignStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventConsumerService {

    private final Map<CampaignType, CampaignStrategy> strategies;
    private final String eventTopic = "event";
    /**
     * Create an EventConsumerService by indexing provided CampaignStrategy implementations by their CampaignType.
     *
     * Constructs an unmodifiable map that maps each strategy's CampaignType to its CampaignStrategy instance
     * for efficient lookup when dispatching events.
     *
     * @param strategyList list of CampaignStrategy implementations injected by Spring
     */
    public EventConsumerService(List<CampaignStrategy> strategyList) {
        // 주입받은 전략 리스트를 Map 형태로 변환하여, 캠페인 타입으로 쉽게 찾을 수 있도록 합니다.
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(CampaignStrategy::getType, Function.identity()));
    }

    /**
     * Dispatches a consumed Kafka event to the matching CampaignStrategy based on its CampaignType.
     *
     * If a strategy for the event's campaign type exists, its {@code process} method is invoked; otherwise a warning is logged.
     *
     * @param event the consumed Kafka event containing the campaign type and associated payload
     */
    @KafkaListener(topics = eventTopic, groupId = "axon-group")
    public void consume(KafkaProducerDto event) {
        log.info("Consumed message: {}", event);

        CampaignType type = event.getCampaignType();
        CampaignStrategy strategy = strategies.get(type);

        if (strategy != null) {
            strategy.process(event);
        } else {
            log.warn("지원하지 않는 캠페인 타입입니다: {}", type);
        }
    }
}