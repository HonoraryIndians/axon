package com.axon.core_service.service.strategy;

import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;

public interface CampaignStrategy {
    void process(CampaignActivityKafkaProducerDto event);
    CampaignActivityType getType();
}
