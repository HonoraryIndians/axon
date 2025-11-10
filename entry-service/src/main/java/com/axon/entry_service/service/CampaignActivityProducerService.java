package com.axon.entry_service.service;

import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampaignActivityProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes the given campaign activity message to the specified Kafka topic.
     *
     * @param topic the Kafka topic name to which the message will be sent
     * @param msg the campaign activity payload to publish
     */
    public void send(String topic, CampaignActivityKafkaProducerDto msg){
        kafkaTemplate.send(topic, msg);
        //System.out.println("Send to " +  topic + " : " + msg);
    }
}