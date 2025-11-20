package com.axon.core_service.utils.fakedata;

import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.purchase.PurchaseType;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class FakePurchaseDataGenerator {
    private final PurchaseRepository purchaseRepository;
    private final Faker faker = new Faker(new Locale("ko"));
    private final Random random = new Random();

    @Transactional
    public List<Purchase> generateFakePurchaseData(int count, long userIdRange, long productIdRange, long campaignActivityIdRange, int daysInPast) {
        List<Purchase> purchases = new ArrayList<>();
        Instant now = Instant.now();

        for (int i=0; i<count; i++) {
            PurchaseType purchaseType = random.nextInt(100) < 70 ? PurchaseType.SHOP : PurchaseType.CAMPAIGNACTIVITY;

            Long userId = faker.number().numberBetween(1L, userIdRange+1);
            Long productId = faker.number().numberBetween(1L, productIdRange+1);
            Long campaignActivityId = (purchaseType == PurchaseType.CAMPAIGNACTIVITY)
                    ? faker.number().numberBetween(1L, campaignActivityIdRange+1)
                    : null;

            // 가격: 1,000원 ~ 100,000원 (정규분포 적용 가능)
            BigDecimal price = BigDecimal.valueOf(faker.number().numberBetween(1000, 100001));

            // 수량: 1 ~ 5개
            Integer quantity = faker.number().numberBetween(1, 6);

            // 과거 랜덤 날짜 생성
            long daysAgo = faker.number().numberBetween(0, daysInPast);
            Instant purchasedAt = now.minus(daysAgo, ChronoUnit.DAYS)
                    .minus(faker.number().numberBetween(0, 24), ChronoUnit.HOURS)
                    .minus(faker.number().numberBetween(0, 60), ChronoUnit.MINUTES);

            Purchase purchase = Purchase.builder()
                    .userId(userId)
                    .productId(productId)
                    .campaignActivityId(campaignActivityId)
                    .purchaseType(purchaseType)
                    .price(price)
                    .quantity(quantity)
                    .purchasedAt(purchasedAt)
                    .build();
            purchases.add(purchase);
        }
        List<Purchase> saved =  purchaseRepository.saveAll(purchases);
        log.info("사용자 구매 기록 [Fake]정보 {}건 생성", count);
        return saved;
    }
}
