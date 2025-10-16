package com.axon.core_service.service.strategy;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.Kafka_ProducerDto;

public interface CampaignStrategy {
    void process(Kafka_ProducerDto event);
    CampaignType getType();
}
