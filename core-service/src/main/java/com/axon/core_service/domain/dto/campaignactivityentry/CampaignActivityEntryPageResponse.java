package com.axon.core_service.domain.dto.campaignactivityentry;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;

@Getter
@Builder
public class CampaignActivityEntryPageResponse {

    private final Long campaignActivityId;
    private final CampaignActivityEntryStatus statusFilter;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final List<CampaignActivityEntrySummary> entries;

    /**
     * Create a paginated response DTO for campaign activity entries.
     *
     * Converts the given Page of CampaignActivityEntry into a CampaignActivityEntryPageResponse by mapping
     * page content to entry summaries and copying pagination metadata.
     *
     * @param campaignActivityId the identifier of the campaign activity these entries belong to
     * @param status             an optional status filter applied to the entries; may be null
     * @param page               the Spring Data Page containing CampaignActivityEntry elements and pagination info
     * @return                   a CampaignActivityEntryPageResponse containing entry summaries and pagination fields
     */
    public static CampaignActivityEntryPageResponse from(Long campaignActivityId,
                                                         @Nullable CampaignActivityEntryStatus status,
                                                         Page<CampaignActivityEntry> page) {
        List<CampaignActivityEntrySummary> items = page.getContent().stream()
                .map(CampaignActivityEntrySummary::from)
                .toList();

        return CampaignActivityEntryPageResponse.builder()
                .campaignActivityId(campaignActivityId)
                .statusFilter(status)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .entries(items)
                .build();
    }
}