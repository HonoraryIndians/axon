package com.axon.core_service.domain.purchase;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "purchases",
    indexes = {
        @Index(name = "idx_purchase_activity_lookup", columnList = "campaign_activity_id, purchase_type, purchase_at"),
        @Index(name = "idx_purchase_user_history", columnList = "user_id, purchase_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_campaign_purchase_user",
            columnNames = {"campaign_activity_id", "user_id"}
        )
    }
)
@Getter
@NoArgsConstructor
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "campaign_activity_id")
    private Long campaignActivityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 20)
    private PurchaseType purchaseType;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "purchase_at", nullable = false)
    private LocalDateTime purchaseAt;

    @Builder
    public Purchase(Long userId, Long productId, Long campaignActivityId,
                     PurchaseType purchaseType, BigDecimal price,
                     Integer quantity, Instant purchasedAt) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.purchaseType = Objects.requireNonNull(purchaseType, "purchaseType must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.quantity = quantity != null ? quantity : 1;
        this.purchaseAt = purchasedAt != null
            ? LocalDateTime.ofInstant(purchasedAt, ZoneId.of("Asia/Seoul"))
            : LocalDateTime.now();

        // CAMPAIGN 타입이면 campaignActivityId 필수
        if (purchaseType == PurchaseType.CAMPAIGNACTIVITY) {
            this.campaignActivityId = Objects.requireNonNull(
                    campaignActivityId,
                    "campaignActivityId must not be null for CAMPAIGN type"
            );
        } else {
            this.campaignActivityId = campaignActivityId;
        }
    }
}
