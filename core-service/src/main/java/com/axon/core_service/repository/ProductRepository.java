package com.axon.core_service.repository;

import com.axon.core_service.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Retrieve a Product by its id while applying a pessimistic write lock to the selected row.
     *
     * @param id the product id to look up
     * @return an Optional containing the Product if found, or {@code Optional.empty()} if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(Long id);
}