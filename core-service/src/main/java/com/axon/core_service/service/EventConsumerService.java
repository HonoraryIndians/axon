package com.axon.core_service.service;

import com.axon.core_service.domain.dto.Kafka_ProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventConsumerService {

    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics="event", groupId = "event-consuming-group")
    public void consume(Kafka_ProducerDto data) {
        log.info("Received data: {}", data);

        String eventKey=  "event" + data.getEventId();
        Long addResult = redisTemplate.opsForSet().add(eventKey, String.valueOf(data.getUserId())); //redis set에 userId추가.
        //결과가 1이면 최초응모, 0이면 중복 응모
        if (addResult != null && addResult == 1) {
            Long currentEntries=  redisTemplate.opsForSet().size(eventKey);
            int limit = 100; // TODO: 추후 이벤트별로 limit 설정 가능하도록 변경

            if (currentEntries != null && currentEntries <= limit) {
                //선착순 성공
                log.info("선착순 성공, User Id: {}, Event ID: {}, 현재 인원: {}/{}", data.getUserId(), eventKey, currentEntries, limit);
            }else {
                //선착순 마감
                log.info("선착순 마감, User Id: {}, Event ID: {}", data.getUserId(), eventKey);
            }
        }else {
            //중복 응모
            log.info("중복 응모, User Id: {}, Event ID: {}", data.getUserId(), eventKey);
        }
    }
}
