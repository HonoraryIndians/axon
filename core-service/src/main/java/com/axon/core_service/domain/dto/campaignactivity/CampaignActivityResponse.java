package com.axon.core_service.domain.dto.campaignactivity;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.messaging.CampaignActivityType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignActivityResponse {

    private final Long id;
    private final Long campaignId;
    private final String name;
    private final Integer limitCount;
    private final CampaignActivityStatus status;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final CampaignActivityType activityType;
    private final LocalDateTime createdAt;
    private final Long participantCount;

    public static CampaignActivityResponse from(CampaignActivity campaignActivity) {
        return from(campaignActivity, null);
    }

    public static CampaignActivityResponse from(CampaignActivity campaignActivity, Long participantCount) {
        return CampaignActivityResponse.builder()
                .id(campaignActivity.getId())
                .campaignId(campaignActivity.getCampaignId())
                .name(campaignActivity.getName())
                .limitCount(campaignActivity.getLimitCount())
                .status(campaignActivity.getStatus())
                .startDate(campaignActivity.getStartDate())
                .endDate(campaignActivity.getEndDate())
                .activityType(campaignActivity.getActivityType())
                .createdAt(campaignActivity.getCreatedAt())
                .participantCount(participantCount)
                .build();
    }
}
