package com.axon.core_service.domain.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long stock; // 재고 수량

    @Column(nullable = false)
    private java.math.BigDecimal price; // 상품 가격

    // @Version
    /**
     * Creates a Product with the given name, initial stock quantity, and price.
     *
     * @param productName the product's name
     * @param stock       the initial stock quantity (number of items)
     * @param price       the product's price
     */

    public Product(String productName, Long stock, java.math.BigDecimal price) {
        this.productName = productName;
        this.stock = stock;
        this.price = price;
    }

    /**
     * Reduces the product's stock by the specified quantity.
     *
     * @param quantity the amount to subtract from the current stock
     * @throws IllegalStateException if subtracting `quantity` would make stock less
     *                               than zero
     */
    public void decreaseStock(long quantity) {
        if (this.stock - quantity < 0) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stock -= quantity;
    }
}