package com.axon.core_service.domain.dto.campaign;

import com.axon.core_service.domain.campaign.CampaignStatus;
import com.axon.messaging.EventType;
import jakarta.validation.constraints.Future;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignRequest {
    @NotBlank
    private String name;

    @NotNull
    private EventType type;

    private Long targetSegmentId;

    private String rewardType;

    private String rewardPayload;

    @Future
    private LocalDateTime startAt;

    @Future
    private LocalDateTime endAt;

    @NotNull
    private CampaignStatus status;
}
