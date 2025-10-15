package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.CampaignType;
import com.axon.core_service.domain.dto.Kafka_ProducerDto;

public interface CampaignStrategy {
    void process(Kafka_ProducerDto event);
    CampaignType getType();
}
