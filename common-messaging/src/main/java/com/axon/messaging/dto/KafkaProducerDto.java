package com.axon.messaging.dto;

import com.axon.messaging.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaProducerDto {
    private EventType eventType;
    private Long eventId;
    private Long userId;
    private Long productId;
    private Long timestamp;
}
