package com.axon.core_service.service.purchase;


import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {
    private final PurchaseRepository purchaseRepository;
    @Transactional
    public void createPurchase(PurchaseInfoDto info) {
        Purchase purchase = new Purchase(info.userId(),
                info.productId(),
                info.campaignActivityId(),
                info.purchaseType(),
                info.price(),
                info.quantity(),
                info.purchasedAt()
                );
        purchaseRepository.save(purchase);
        log.info("구매 내역 저장! 사용자: {}, 상품: {}, 캠페인: {}", purchase.getUserId(), purchase.getProductId(), purchase.getCampaignActivityId());
    }
}
