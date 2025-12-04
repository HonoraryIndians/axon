package com.axon.core_service.domain.dto.campaign;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignRequest {
    @NotBlank
    private String name;

    private Long targetSegmentId;

    private String rewardType;

    private String rewardPayload;

    @Future
    private LocalDateTime startAt;

    @Future
    private LocalDateTime endAt;

    @PositiveOrZero
    private BigDecimal budget;
}
