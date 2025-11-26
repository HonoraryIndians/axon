package com.axon.core_service.controller.fakedata;

import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.utils.fakedata.FakePurchaseDataGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fake/data")
@RequiredArgsConstructor
public class FakeDataController {
    private final FakePurchaseDataGenerator fakePurchaseDataGenerator;

    @PostMapping("/purchases")
    public ResponseEntity<Map<String, Object>> generatePurchases(
            @RequestParam(defaultValue = "1000") int count,
            @RequestParam(defaultValue = "100") long userIdRange,
            @RequestParam(defaultValue = "50") long productIdRange,
            @RequestParam(defaultValue = "20") long campaignActivityIdRange,
            @RequestParam(defaultValue = "365") int daysInPast
    ) {
        log.info("🎲 가짜 구매 데이터 생성 요청 - count: {}, userIdRange: {}, productIdRange: {}, campaignActivityIdRange: {}, daysInPast: {}", count, userIdRange, productIdRange, campaignActivityIdRange, daysInPast);

        List<Purchase> purchases = fakePurchaseDataGenerator.generateFakePurchaseData(count, userIdRange, productIdRange, campaignActivityIdRange, daysInPast);

        return ResponseEntity.ok(Map.of(
                "message", "가짜 구매 데이터 생성 완료",
                "count", purchases.size(),
                "userIdRange", "1-" + userIdRange,
                "productIdRange", "1-" + productIdRange,
                "campaignActivityIdRange", "1-" + campaignActivityIdRange,
                "daysInPast", daysInPast
        ));
    }

}
