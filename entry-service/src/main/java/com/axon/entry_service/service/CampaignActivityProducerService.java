package com.axon.entry_service.service;

import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampaignActivityProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(String topic, CampaignActivityKafkaProducerDto msg){
        kafkaTemplate.send(topic, msg);
        //System.out.println("Send to " +  topic + " : " + msg);
    }
}
