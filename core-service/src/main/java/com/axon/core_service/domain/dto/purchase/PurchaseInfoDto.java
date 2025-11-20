package com.axon.core_service.domain.dto.purchase;

import com.axon.core_service.domain.purchase.PurchaseType;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseInfoDto(
        Long campaignActivityId,
        Long userId,
        Long productId,
        Instant occurredAt,
        PurchaseType purchaseType,
        BigDecimal price,
        Integer quantity,
        Instant purchasedAt
) {
}
