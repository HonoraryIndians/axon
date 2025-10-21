package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.campaign.CampaignStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;


@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequest {
    @NotBlank
    private String name;

    @PositiveOrZero
    private Integer limitCount;

    @NotNull
    private CampaignStatus status;
}
