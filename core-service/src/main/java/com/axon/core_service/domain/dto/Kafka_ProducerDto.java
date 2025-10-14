package com.axon.core_service.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Kafka_ProducerDto {
    private int eventId;
    private int userId;
    private int productId;
    private Long timestamp;

}
