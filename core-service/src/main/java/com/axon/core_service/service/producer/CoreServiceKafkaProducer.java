package com.axon.core_service.service.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoreServiceKafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(String topic, Object payload) {
        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("카프카 전송 실패 topic={} payload={}", topic, payload, ex);
                    } else if (result != null) {
                        log.info("카프카 전송 완료 topic={} partition={} offset={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
