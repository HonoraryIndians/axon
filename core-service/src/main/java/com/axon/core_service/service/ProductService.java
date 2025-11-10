package com.axon.core_service.service;

import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 재고를 1 감소시킵니다.
     * 이 메소드는 상위 서비스에서 트랜잭션으로 호출되어야 합니다.
     * @param productId 상품 ID
     */
    @Transactional
    public void decreaseStock(Long productId) {
        // 1. 비관적 락을 걸고 상품 정보를 조회합니다.
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId)); // TODO: Custom Exception으로 변경

        // 2. Product 엔티티 내부에 있는 재고 감소 메소드를 호출합니다.
        product.decreaseStock(1);

        // 3. @Transactional 어노테이션에 의해, 메소드가 종료될 때 변경된 내용이 DB에 자동으로 저장(UPDATE)됩니다.
    }
}
