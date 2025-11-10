package com.axon.core_service.domain.dto.campaign;

import com.axon.core_service.domain.campaign.Campaign;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CampaignResponse {
    private final Long id;
    private final String name;
    private final Long targetSegmentId;
    private final String rewardType;
    private final String rewardPayload;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;

    /**
     * Creates a CampaignResponse DTO populated from the given Campaign domain object.
     *
     * @param campaign the Campaign whose fields will be copied into the response DTO
     * @return a CampaignResponse with fields populated from the provided Campaign
     */
    public static CampaignResponse from(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .targetSegmentId(campaign.getTargetSegmentId())
                .rewardType(campaign.getRewardType())
                .rewardPayload(campaign.getRewardPayload())
                .startAt(campaign.getStartAt())
                .endAt(campaign.getEndAt())
                .build();
    }
}