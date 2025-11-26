package com.axon.core_service.domain.dto.campaignactivity;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.messaging.CampaignActivityType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignActivityResponse {

    private final Long id;
    private final Long campaignId;
    private final Long productId;
    private final String name;
    private final Integer limitCount;
    private final CampaignActivityStatus status;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final CampaignActivityType activityType;
    private final LocalDateTime createdAt;
    private final Long participantCount;
    private final List<FilterDetail> filters;
    private final String imageUrl;

    private final String productName;
    private final Integer originalPrice;
    private final Integer price;
    private final Integer quantity;

    /**
     * Create a CampaignActivityResponse from a CampaignActivity with no participant
     * count.
     *
     * @param campaignActivity the source CampaignActivity to map into the response
     * @return a CampaignActivityResponse populated from the given CampaignActivity
     *         with `participantCount` set to {@code null}
     */
    public static CampaignActivityResponse from(CampaignActivity campaignActivity) {
        return from(campaignActivity, null);
    }

    /**
     * Create a CampaignActivityResponse DTO from a CampaignActivity entity,
     * optionally including participant count.
     *
     * @param campaignActivity the source domain entity whose fields are copied into
     *                         the DTO
     * @param participantCount the participant count to assign to the DTO; may be
     *                         {@code null}
     * @return a CampaignActivityResponse populated from the provided entity and
     *         participant count
     */
    public static CampaignActivityResponse from(CampaignActivity campaignActivity, Long participantCount) {
        return CampaignActivityResponse.builder()
                .id(campaignActivity.getId())
                .campaignId(campaignActivity.getCampaignId())
                .productId(campaignActivity.getProductId())
                .productName(
                        campaignActivity.getProduct() != null ? campaignActivity.getProduct().getProductName() : null)
                .originalPrice(
                        campaignActivity.getProduct() != null ? campaignActivity.getProduct().getPrice().intValue()
                                : null)
                .name(campaignActivity.getName())
                .limitCount(campaignActivity.getLimitCount())
                .status(campaignActivity.getStatus())
                .startDate(campaignActivity.getStartDate())
                .endDate(campaignActivity.getEndDate())
                .activityType(campaignActivity.getActivityType())
                .createdAt(campaignActivity.getCreatedAt())
                .participantCount(participantCount)
                .filters(campaignActivity.getFilters())
                .imageUrl(campaignActivity.getImageUrl())
                .price(campaignActivity.getPrice() != null ? campaignActivity.getPrice().intValue() : null)
                .quantity(campaignActivity.getQuantity())
                .build();
    }
}