package com.axon.core_service.service.purchase;


import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {
    private final PurchaseRepository purchaseRepository;

    @Transactional
    public void createPurchaseBatch(List<PurchaseInfoDto> purchases) {
        if (purchases.isEmpty()) {
            return;
        }

        log.info("Creating {} purchase records", purchases.size());

        List<Purchase> purchaseEntities = purchases.stream()
                .map(info -> new Purchase(
                        info.userId(),
                        info.productId(),
                        info.campaignActivityId(),
                        info.purchaseType(),
                        info.price(),
                        info.quantity(),
                        info.purchasedAt()
                ))
                .toList();

        purchaseRepository.saveAll(purchaseEntities);
        log.info("Saved {} purchase records", purchaseEntities.size());
    }
}
