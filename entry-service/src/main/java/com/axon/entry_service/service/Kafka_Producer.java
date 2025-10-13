package com.axon.entry_service.service;

import com.axon.entry_service.dto.Kafka_ProducerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Kafka_Producer {
    private final KafkaTemplate<String, Object> kafkaTemplate;


    public void KafkasendMessage(String topic, Kafka_ProducerDto msg){
        kafkaTemplate.send(topic, msg);
        //System.out.println("Send to " +  topic + " : " + msg);
    }
}
