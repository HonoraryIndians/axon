package com.axon.core_service.service;

import com.axon.core_service.domain.CampaignType;
import com.axon.core_service.domain.dto.Kafka_ProducerDto;
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

    // Spring이 시작될 때, CampaignStrategy 인터페이스를 구현한 모든 Bean을 찾아 리스트로 주입해줍니다.
    public EventConsumerService(List<CampaignStrategy> strategyList) {
        // 주입받은 전략 리스트를 Map 형태로 변환하여, 캠페인 타입으로 쉽게 찾을 수 있도록 합니다.
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(CampaignStrategy::getType, Function.identity()));
    }

    @KafkaListener(topics = "AXON-topic", groupId = "axon-group")
    public void consume(Kafka_ProducerDto event) {
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
