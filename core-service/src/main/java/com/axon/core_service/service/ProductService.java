package com.axon.core_service.service;

import  com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Decrements the stock quantity of the specified product by one.
     *
     * @param stockDecreases the identifier of the product whose stock will be decremented
     * @throws IllegalArgumentException if no product exists with the given id
     */
    @Transactional
    public void decreaseStockBatch(Map<Long, Integer> stockDecreases) {
        if (stockDecreases.isEmpty()) {
            return;
        }

        log.info("Decreasing stock for {} products", stockDecreases.size());

        // 1. 해당 상품들을 비관적 락으로 조회
        List<Product> products = productRepository.findByIdInWithPessimisticLock(new ArrayList<>(stockDecreases.keySet()));

        // 2. 각 상품의 재고 감소
        for (Product product : products) {
            Integer decreaseAmount = stockDecreases.get(product.getId());
            if (decreaseAmount != null) {
                product.decreaseStock(decreaseAmount);
            }
        }

        // 3. Dirty Checking으로 자동 UPDATE
        log.info("Stock decreased for {} products", products.size());
    }

    /**
     * Decreases stock for a single product (used for SHOP purchases).
     *
     * @param productId the product ID
     * @param quantity  the amount to decrease
     */
    @Transactional
    public void decreaseStock(Long productId, Integer quantity) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        product.decreaseStock(quantity);
        log.info("Stock decreased for productId={}, amount={}", productId, quantity);
    }

    /**
     * Syncs campaign product stock after campaign ends.
     *
     * For FCFS campaigns, stock is managed by Redis counter during the campaign.
     * This method syncs MySQL Product.stock when the campaign ends.
     *
     * Formula: finalStock = initialStock - soldCount
     *
     * @param productId the campaign product ID
     * @param soldCount number of items sold (from Redis counter)
     */
    @Transactional
    public void syncCampaignStock(Long productId, Long soldCount) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        long expectedStock = product.getStock() - soldCount;

        if (expectedStock < 0) {
            log.warn("Campaign over-sold! productId={}, currentStock={}, soldCount={}",
                    productId, product.getStock(), soldCount);
            expectedStock = 0;
        }

        product.decreaseStock(soldCount);
        log.info("Campaign stock synced: productId={}, soldCount={}, finalStock={}",
                productId, soldCount, product.getStock());
    }
}