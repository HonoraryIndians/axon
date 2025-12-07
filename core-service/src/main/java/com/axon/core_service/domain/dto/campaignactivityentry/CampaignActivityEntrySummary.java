package com.axon.core_service.domain.dto.campaignactivityentry;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignActivityEntrySummary {

    private final Long entryId;
    private final Long userId;
    private final Long productId;
    private final CampaignActivityEntryStatus status;
    private final LocalDateTime requestedAt;
    private final LocalDateTime processedAt;
    private final String info;

    /**
     * Create a CampaignActivityEntrySummary from a CampaignActivityEntry.
     *
     * @param entry the source CampaignActivityEntry to convert
     * @return a CampaignActivityEntrySummary populated with the source entry's fields
     */
    public static CampaignActivityEntrySummary from(CampaignActivityEntry entry) {
        return CampaignActivityEntrySummary.builder()
                .entryId(entry.getId())
                .userId(entry.getUserId())
                .productId(entry.getProductId())
                .status(entry.getStatus())
                .requestedAt(entry.getRequestedAt())
                .processedAt(entry.getProcessedAt())
                .info(entry.getInfo())
                .build();
    }
}