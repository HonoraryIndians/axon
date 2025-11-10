package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.domain.dto.campaignactivityentry.CampaignActivityEntryPageResponse;
import com.axon.core_service.exception.CampaignActivityNotFoundException;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.core_service.repository.CampaignActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignActivityEntryQueryService {

    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryRepository campaignActivityEntryRepository;

    /**
     * Retrieve a paginated view of entries for a specific campaign activity.
     *
     * @param campaignActivityId the ID of the campaign activity to fetch entries for
     * @param status             optional filter to restrict entries to the given status; pass `null` to include all statuses
     * @param pageable           pagination and sorting information for the result set
     * @return                   a CampaignActivityEntryPageResponse containing the requested entries and pagination metadata
     * @throws CampaignActivityNotFoundException if no campaign activity exists with the provided `campaignActivityId`
     */
    @Transactional(readOnly = true)
    public CampaignActivityEntryPageResponse findEntries(Long campaignActivityId,
                                                         @Nullable CampaignActivityEntryStatus status,
                                                         Pageable pageable) {
        CampaignActivity campaignActivity = campaignActivityRepository.findById(campaignActivityId)
                .orElseThrow(() -> new CampaignActivityNotFoundException(campaignActivityId));

        Page<CampaignActivityEntry> entries = (status == null)
                ? campaignActivityEntryRepository.findByCampaignActivity_Id(campaignActivity.getId(), pageable)
                : campaignActivityEntryRepository.findByCampaignActivity_IdAndStatus(campaignActivity.getId(), status, pageable);

        return CampaignActivityEntryPageResponse.from(campaignActivityId, status, entries);
    }
}