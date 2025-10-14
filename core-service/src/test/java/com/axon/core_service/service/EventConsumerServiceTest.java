//package com.axon.core_service.service;
//
//import com.axon.core_service.domain.dto.Kafka_ProducerDto;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.kafka.core.KafkaTemplate;
//
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//class EventConsumerServiceTest {
//
//    @Autowired
//    private KafkaTemplate<String, Kafka_ProducerDto> kafkaTemplate;
//
//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;
//
//    private final String topic = "AXON-topic";
//
//    @Test
//    @DisplayName("Kafka로 응모 메시지를 보내면, Redis Set에 userId가 성공적으로 추가된다.")
//    void consume_and_add_to_redis_set() throws InterruptedException {
//        // given: 테스트할 데이터 준비
//        int eventId = 1;
//        int userId = 12345;
//        int productId = 101;
//        long timestamp = System.currentTimeMillis();
//        Kafka_ProducerDto testDto = new Kafka_ProducerDto(eventId, userId, productId, timestamp);
//
//        String eventKey = "event:" + eventId;
//        redisTemplate.delete(eventKey); // 테스트 전 이전 데이터 삭제
//
//        // when: Kafka 토픽으로 메시지 전송
//        kafkaTemplate.send(topic, testDto);
//
//        // then: Consumer가 메시지를 처리할 시간을 잠시 기다린 후, 결과를 검증
//        TimeUnit.SECONDS.sleep(3); // 비동기 처리를 위한 대기
//
//        Boolean isMember = redisTemplate.opsForSet().isMember(eventKey, String.valueOf(userId));
//        assertThat(isMember).isTrue();
//    }
//}
