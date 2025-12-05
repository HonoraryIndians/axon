package com.axon.core_service.service.batch;


import com.axon.core_service.service.strategy.CampaignStrategy;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;

import java.util.List;
/**
 * 배치 처리를 지원하는 캠페인 전략 인터페이스

 * 마이크로 배치 최적화를 위해 여러 메시지를 한번에 처리
 */
public interface BatchStrategy extends CampaignStrategy {
    /**
     * 여러 캠페인 활동 메시지를 배치로 처리
     *
     * @param messages 배치 처리할 메시지 리스트 (보통 50개 단위)
     */
    void processBatch(List<CampaignActivityKafkaProducerDto> messages);
}
