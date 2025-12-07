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

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "stock", nullable = false)
    private Long stock; // 재고 수량

    @Column(name = "price", nullable = false)
    private java.math.BigDecimal price; // 정상가

    @Column(name = "category")
    private String category; // 제품 카테고리 (e.g., "Electronics", "Fashion")

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "brand")
    private String brand;

    @Column(name = "discount_rate")
    private Integer discountRate;

    // @Version
    /**
     * Creates a Product with the given name, stock, price and category.
     *
     * @param productName the product's name
     * @param stock       the initial stock quantity
     * @param price       the price
     * @param category    the product category
     */
    public Product(String productName, Long stock, java.math.BigDecimal price, String category) {
        this(productName, stock, price, category, null, null);
    }

    public Product(String productName, Long stock, java.math.BigDecimal price, String category, String brand,
            Integer discountRate) {
        this.productName = productName;
        this.stock = stock;
        this.price = price;
        this.category = category;
        this.brand = brand;
        this.discountRate = discountRate;
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