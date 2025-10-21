package com.axon.core_service.domain.dto.campaign;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaign.CampaignStatus;
import com.axon.messaging.CampaignType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CampaignResponse {
    private final Long id;
    private final String name;
    private final CampaignType type;
    private final Long targetSegmentId;
    private final String rewardType;
    private final String rewardPayload;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final CampaignStatus status;

    public static CampaignResponse from(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .type(campaign.getType())
                .targetSegmentId(campaign.getTargetSegmentId())
                .rewardType(campaign.getRewardType())
                .rewardPayload(campaign.getRewardPayload())
                .startAt(campaign.getStartAt())
                .endAt(campaign.getEndAt())
                .status(campaign.getStatus())
                .build();
    }
}
