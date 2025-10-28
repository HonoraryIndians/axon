package com.axon.core_service.service;

import com.axon.core_service.service.strategy.CampaignStrategy;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.KafkaProducerDto;
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
    private final String topic = "event";

    public CampaignActivityConsumerService(List<CampaignStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(CampaignStrategy::getType, Function.identity()));
    }

    @KafkaListener(topics = topic, groupId = "axon-group")
    public void consume(KafkaProducerDto message) {
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
