package com.axon.entry_service.service;

import com.axon.messaging.dto.KafkaProducerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Kafka_Producer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish the given KafkaProducerDto to the specified Kafka topic.
     *
     * @param topic the target Kafka topic name
     * @param msg   the message payload to publish
     */
    public void KafkasendMessage(String topic, KafkaProducerDto msg){
        kafkaTemplate.send(topic, msg);
        //System.out.println("Send to " +  topic + " : " + msg);
    }
}