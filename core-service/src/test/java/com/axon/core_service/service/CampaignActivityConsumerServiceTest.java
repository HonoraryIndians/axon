package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.repository.ProductRepository;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.KafkaProducerDto;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("oauth")
class CampaignActivityConsumerServiceTest {

    @Autowired
    private KafkaTemplate<String, KafkaProducerDto> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CampaignActivityRepository campaignActivityRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final String topic = "event";
    private final Long productId = 1L;
    private Long campaignActivityId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        campaignActivityRepository.deleteAll();
        campaignRepository.deleteAll();
        kafkaTemplate.flush();

        Set<String> activityKeys = redisTemplate.keys("campaign-activity:*");
        if (activityKeys != null && !activityKeys.isEmpty()) {
            redisTemplate.delete(activityKeys);
        }

        Campaign testCampaign = Campaign.builder()
                .name("테스트 캠페인")
                .build();
        campaignRepository.save(testCampaign);

        CampaignActivity activity = CampaignActivity.builder()
                .campaign(testCampaign)
                .name("선착순 테스트")
                .limitCount(100)
                .status(CampaignActivityStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .activityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                .filters(null)
                .build();
        CampaignActivity saved = campaignActivityRepository.save(activity);
        this.campaignActivityId = saved.getId();

        Product testProduct = new Product("테스트 상품", 100L);
        productRepository.save(testProduct);
    }

    @Test
    @DisplayName("100개의 재고에 300개의 동시 요청이 발생하면, 재고는 0이 되고 100명만 성공해야 한다.")
    void decreaseStock_ConcurrencyTest() throws InterruptedException {
        int numberOfThreads = 300;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            Long userId = (long) i;
            executorService.submit(() -> {
                try {
                    KafkaProducerDto dto = new KafkaProducerDto(
                            CampaignActivityType.FIRST_COME_FIRST_SERVE,
                            campaignActivityId,
                            userId,
                            productId,
                            System.currentTimeMillis()
                    );
                    kafkaTemplate.send(topic, dto);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        Thread.sleep(5000);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0L);
    }
}
