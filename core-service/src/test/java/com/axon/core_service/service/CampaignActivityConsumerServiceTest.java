package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.repository.ProductRepository;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("oauth")
class CampaignActivityConsumerServiceTest {

    @Autowired
    private KafkaTemplate<String, CampaignActivityKafkaProducerDto> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CampaignActivityRepository campaignActivityRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    private final String topic = KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND;
    private final Long productId = 1L;
    private Long campaignActivityId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        campaignActivityRepository.deleteAll();
        campaignRepository.deleteAll();
        kafkaTemplate.flush();

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
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            Long userId = (long) i;
            executorService.submit(() -> {
                try {
                    CampaignActivityKafkaProducerDto dto = new CampaignActivityKafkaProducerDto(
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
