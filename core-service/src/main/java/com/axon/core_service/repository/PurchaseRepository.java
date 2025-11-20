package com.axon.core_service.repository;


import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.purchase.PurchaseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 사용자별 구매 내역 조회
    List<Purchase> findByUserId(Long userId);

    // 상품별 구매 내역 조회
    List<Purchase> findByProductId(Long productId);

    // 구매 타입별 조회
    List<Purchase> findByPurchaseType(PurchaseType purchaseType);

    // 캠페인별 구매 내역 조회
    List<Purchase> findByCampaignActivityId(Long campaignActivityId);
}
