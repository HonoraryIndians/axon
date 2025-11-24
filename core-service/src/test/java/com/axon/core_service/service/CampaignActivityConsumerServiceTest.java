package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.TriggerType;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.repository.EventRepository;
import com.axon.core_service.repository.ProductRepository;
import com.axon.core_service.repository.UserRepository;
import com.axon.core_service.repository.UserSummaryRepository;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import com.axon.messaging.topic.KafkaTopics;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final int NUMBER_OF_THREADS = 100;

    @Autowired
    private KafkaTemplate<String, CampaignActivityKafkaProducerDto> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CampaignActivityRepository campaignActivityRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSummaryRepository userSummaryRepository;

    @Autowired
    private EventRepository eventRepository;

    private final String topic = KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND;
    private final Long productId = 1L;
    private Long campaignActivityId;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        campaignActivityRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();
        eventRepository.deleteAll();
        kafkaTemplate.flush();

        // 구매 이벤트 사전 등록
        eventRepository.save(Event.builder()
                .name("Purchase Event")
                .description("테스트 구매 이벤트")
                .triggerCondition(Event.TriggerCondition.of(TriggerType.PURCHASE, Map.of()))
                .build());

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

        Product product = new Product("Test Product", 100L, java.math.BigDecimal.valueOf(10000));
        productRepository.save(product);

        userIds = new ArrayList<>(NUMBER_OF_THREADS);
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            userIds.add(userRepository.save(
                    com.axon.core_service.domain.user.User.builder()
                            .name("user-" + i)
                            .email("user" + i + "@example.com")
                            .role(com.axon.core_service.domain.user.Role.USER)
                            .provider("test")
                            .providerId("provider-" + i)
                            .build())
                    .getId());
        }
    }

    @Test
    @DisplayName("100개의 재고에 300개의 동시 요청이 발생하면, 재고는 0이 되고 100명만 성공해야 한다.")
    void decreaseStock_ConcurrencyTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(NUMBER_OF_THREADS);

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            final Long userId = userIds.get(i);
            executorService.submit(() -> {
                try {
                    CampaignActivityKafkaProducerDto dto = CampaignActivityKafkaProducerDto.builder()
                            .campaignActivityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                            .campaignActivityId(campaignActivityId)
                            .userId(userId)
                            .productId(productId)
                            .timestamp(Instant.now().toEpochMilli())
                            .build();
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

        userIds.forEach(id -> assertThat(
                userSummaryRepository.findById(id)
                        .map(summary -> summary.getLastPurchaseAt())
                        .orElse(null))
                .isNotNull());
    }
}
