package com.axon.core_service.service.strategy;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;

public interface CampaignStrategy {
    /**
 * Processes a campaign event represented by a KafkaProducerDto.
 *
 * @param event the campaign event payload to process
 */
void process(KafkaProducerDto event);
    /**
 * Identifies the campaign type handled by this strategy.
 *
 * @return the CampaignType that this strategy handles
 */
CampaignType getType();
}