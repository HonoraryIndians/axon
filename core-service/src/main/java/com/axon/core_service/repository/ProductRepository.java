package com.axon.core_service.repository;

import com.axon.core_service.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 비관적 쓰기 락(Pessimistic Write Lock)을 사용하여 상품을 조회합니다.
     * 이 메소드를 호출한 트랜잭션이 완료될 때까지 다른 트랜잭션은 해당 레코드에 접근할 수 없습니다.
     * @param id 상품 ID
     * @return Product
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(Long id);
}
