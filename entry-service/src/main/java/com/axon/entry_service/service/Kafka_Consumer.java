package com.axon.entry_service.service;

import com.axon.entry_service.dto.Kafka_ProducerDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class Kafka_Consumer {
    @KafkaListener(
            topics = "event",
            groupId = "axon-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void KafkaReceiveMessage(Kafka_ProducerDto msg) {
        //System.out.println("Received Message: " + msg);
    }
}
