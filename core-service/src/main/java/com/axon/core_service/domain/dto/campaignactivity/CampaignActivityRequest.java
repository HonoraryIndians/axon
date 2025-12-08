package com.axon.core_service.domain.dto.campaignactivity;

import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.domain.product.Product;
import com.axon.messaging.CampaignActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignActivityRequest {

    private Long campaignId;

    @NotBlank
    private String name;

    @PositiveOrZero
    private Integer limitCount;

    @NotNull
    private CampaignActivityStatus status;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    @NotNull
    private CampaignActivityType activityType;

    private List<FilterDetail> filters;

    @NotNull
    private BigDecimal price;

    private Long productId;

    private Long couponId;

    @NotNull
    private Integer quantity;

    private BigDecimal budget;

    private String imageUrl;
}
