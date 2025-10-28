package com.axon.core_service.service;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.repository.EventRepository;
import com.axon.core_service.repository.ProductRepository;
import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.messaging.EventType;
import com.axon.messaging.dto.KafkaProducerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("oauth") // oauth 프로필 활성화
class EventConsumerServiceTest {

    @Autowired
    private KafkaTemplate<String, KafkaProducerDto> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EventRepository eventRepository;

    private final String topic = "event";
    private Long eventId;
    private final Long productId = 1L;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 각 테스트 실행 전에 DB를 깨끗하게 비움
        productRepository.deleteAll();
        eventRepository.deleteAll();
        campaignRepository.deleteAll(); // Campaign도 삭제
        kafkaTemplate.flush();
        // Redis 선착순 집합 초기화
        Set<String> eventKeys = redisTemplate.keys("event:*");
        if (eventKeys != null && !eventKeys.isEmpty()) {
            redisTemplate.delete(eventKeys);
        }

        // 테스트에 필요한 엔티티 생성 (빌더 패턴 사용)
        Campaign testCampaign = Campaign.builder()
                .name("테스트 캠페인")
                .build();
        campaignRepository.save(testCampaign); // Campaign을 먼저 저장해야 Event에서 참조 가능

        // 실제 Event 생성자에 맞게 모든 인수를 전달
        Event testEvent = new Event(testCampaign, "선착순 테스트", 100, EventStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now().plusDays(1), EventType.FIRST_COME_FIRST_SERVE);
        Event savedEvent = eventRepository.save(testEvent);
        this.eventId = savedEvent.getId(); // DB에서 생성된 실제 Event ID를 할당

        // 실제 Product 생성자에 맞게 모든 인수를 전달
        Product testProduct = new Product("테스트 상품", 100L);
        productRepository.save(testProduct);
    }

    @Test
    @DisplayName("100개의 재고에 300개의 동시 요청이 발생하면, 재고는 0이 되고 100명만 성공해야 한다.")
    void decreaseStock_ConcurrencyTest() throws InterruptedException {
        // given
        int numberOfThreads = 300;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // when
        for (int i = 0; i < numberOfThreads; i++) {
            Long userId = Long.parseLong(String.valueOf(i));
            executorService.submit(() -> {
                try {
                    KafkaProducerDto dto = new KafkaProducerDto(EventType.FIRST_COME_FIRST_SERVE, eventId, userId, productId, System.currentTimeMillis());
                    kafkaTemplate.send(topic, dto);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        Thread.sleep(5000); // 모든 Kafka 메시지가 소비될 시간을 충분히 기다림

        // then
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0L);
    }
}
