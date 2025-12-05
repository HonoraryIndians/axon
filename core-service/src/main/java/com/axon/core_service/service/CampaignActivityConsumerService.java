package com.axon.core_service.service;

import com.axon.core_service.service.batch.BatchStrategy;
import com.axon.core_service.service.strategy.CampaignStrategy;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CampaignActivityConsumerService {

    private final Map<CampaignActivityType, CampaignStrategy> strategies;
    private final int kafkaBatchBuffer = 50;

    // 메시지 버퍼 (Thread-safe Queue)
    private final ConcurrentLinkedQueue<CampaignActivityKafkaProducerDto> buffer = new ConcurrentLinkedQueue<>();
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
        buffer.offer(message);
        log.debug("[Kafka] : Activities's message buffered size={}", buffer.size());

        if(buffer.size() >= kafkaBatchBuffer) {
            flushBatch();
        }
    }
    /**
     * 100ms마다 자동으로 버퍼 플러시
     *
     * 역할: 메시지가 적게 들어와도 100ms 이내에 처리 보장
     */
    @Scheduled(fixedDelay = 100)
    public void scheduledFlush() {
        if (!buffer.isEmpty()) {
            flushBatch();
        }
    }

    /**
     * 버퍼의 메시지를 배치로 처리
     *
     * 역할:
     * 1. 버퍼에서 메시지 추출
     * 2. 타입별로 그룹핑
     * 3. 각 Strategy에 배치 처리 위임
     */
    private synchronized void flushBatch() {
        if (buffer.isEmpty()) {
            return;
        }

        // 1. 버퍼에서 메시지 추출 (최대 BATCH_SIZE개)
        List<CampaignActivityKafkaProducerDto> messages = drainBuffer();

        if (messages.isEmpty()) {
            return;
        }

        log.info("Processing Micro batch: {} messages", messages.size());

        // 2. 타입별로 그룹핑 (FCFS 등)
        Map<CampaignActivityType, List<CampaignActivityKafkaProducerDto>> groupedByType =
                messages.stream()
                        .collect(Collectors.groupingBy(
                                CampaignActivityKafkaProducerDto::getCampaignActivityType
                        ));

        // 3. 각 타입별로 처리
        groupedByType.forEach((type, batch) -> {
            CampaignStrategy strategy = strategies.get(type);

            if (strategy == null) {
                log.warn("지원하지 않는 캠페인 활동 타입입니다: {}", type);
                return;
            }

            try {
                // 배치 처리 지원하면 배치로, 아니면 개별 처리
                if (strategy instanceof BatchStrategy) {
                    ((BatchStrategy) strategy).processBatch(batch);
                    log.info("Batch processed: type={}, count={}", type, batch.size());
                } else {
                    // Fallback: 개별 처리
                    batch.forEach(msg -> {
                        strategy.process(msg);
                        log.info("Consumed message: {}", msg);
                    });
                }
            } catch (Exception e) {
                log.error("Error processing batch for type {}: {}", type, e.getMessage(), e);
            }
        });
    }
    /**
     * 버퍼에서 메시지 추출
     *
     * 역할: Thread-safe하게 버퍼 비우기 (최대 BATCH_SIZE개)
     */
    private List<CampaignActivityKafkaProducerDto> drainBuffer() {
        List<CampaignActivityKafkaProducerDto> drained = new ArrayList<>(kafkaBatchBuffer);

        for (int i = 0; i < kafkaBatchBuffer; i++) {
            CampaignActivityKafkaProducerDto message = buffer.poll();
            if (message == null) {
                break;
            }
            drained.add(message);
        }

        return drained;
    }

    /**
     * 서비스 종료 시 남은 메시지 처리
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Shutting down consumer, flushing remaining messages...");
        flushBatch();
    }

}