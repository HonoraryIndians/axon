package com.axon.core_service.service.strategy;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;

public interface CampaignStrategy {
    void process(KafkaProducerDto event);
    CampaignType getType();
}
