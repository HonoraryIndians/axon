package com.axon.entry_service.controller;

import com.axon.entry_service.dto.Kafka_ProducerDto;
import com.axon.entry_service.service.Kafka_Producer;
import com.axon.entry_service.service.Kafka_Producer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kafka")
@RequiredArgsConstructor
public class Kafka_Controller {
    private final Kafka_Producer producer;
    private final String axon_topic = "AXON-topic";

    @PostMapping("/send")
    public String sendMessage(@RequestBody Kafka_ProducerDto msg) {
        producer.KafkasendMessage(axon_topic, msg);
        return "Message sent successfully || msg : " + msg;
    }
}
