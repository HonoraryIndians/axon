package com.axon.core_service.service.purchase;


import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {
    private final PurchaseRepository purchaseRepository;

    @Transactional
    public void createPurchase(PurchaseInfoDto info) {
        log.info("Creating purchase record for userId={}, productId={}", info.userId(), info.productId());

        Purchase purchase = new Purchase(
                info.userId(),
                info.productId(),
                info.campaignActivityId(),
                info.purchaseType(),
                info.price(),
                info.quantity(),
                info.purchasedAt()
        );

        try {
            purchaseRepository.save(purchase);
            log.info("Saved purchase record for userId={}, productId={}", info.userId(), info.productId());
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate purchase detected: activity={}, user={}",
                    info.campaignActivityId(), info.userId());
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        try {
            purchaseRepository.saveAll(purchaseEntities);
            log.info("Saved {} purchase records", purchaseEntities.size());
        } catch (DataIntegrityViolationException e) {
            // Handle duplicates gracefully - process individually
            log.warn("Duplicate purchases detected in batch, processing individually");
            int saved = 0;
            for (Purchase purchase : purchaseEntities) {
                try {
                    purchaseRepository.save(purchase);
                    saved++;
                } catch (DataIntegrityViolationException ex) {
                    log.debug("Skipping duplicate purchase: activity={}, user={}",
                        purchase.getCampaignActivityId(), purchase.getUserId());
                }
            }
            log.info("Saved {} purchase records ({} duplicates skipped)", saved, purchaseEntities.size() - saved);
        }
    }
}
