package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignActivityEntryRepository extends JpaRepository<CampaignActivityEntry, Long> {

    /**
     * Finds the campaign activity entry for the given campaign activity and user.
     *
     * @param campaignActivityId the ID of the campaign activity
     * @param userId             the ID of the user
     * @return an Optional containing the matching CampaignActivityEntry if present, otherwise empty
     */
    Optional<CampaignActivityEntry> findByCampaignActivity_IdAndUserId(Long campaignActivityId, Long userId);

    /**
     * Retrieves a paginated list of CampaignActivityEntry objects for the specified campaign activity.
     *
     * @param campaignActivityId the ID of the campaign activity whose entries to retrieve
     * @param pageable           pagination and sorting information to apply to the result
     * @return a page of CampaignActivityEntry matching the specified campaign activity ID
     */
    Page<CampaignActivityEntry> findByCampaignActivity_Id(Long campaignActivityId, Pageable pageable);

    /**
     * Retrieves a page of CampaignActivityEntry records for the specified campaign activity and status.
     *
     * @param campaignActivityId the ID of the campaign activity to filter by
     * @param status             the status to filter entries by
     * @param pageable           paging and sorting information
     * @return a page of CampaignActivityEntry objects matching the given campaign activity ID and status
     */
    Page<CampaignActivityEntry> findByCampaignActivity_IdAndStatus(Long campaignActivityId,
                                                                   CampaignActivityEntryStatus status,
                                                                   Pageable pageable);

    /**
     * Count CampaignActivityEntry records associated with a campaign activity.
     *
     * @param campaignActivityId the ID of the campaign activity to count entries for
     * @return the number of CampaignActivityEntry records linked to the specified campaign activity ID
     */
    Long countByCampaignActivity_Id(Long campaignActivityId);
    Long countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
            Long campaignActivityId,
            CampaignActivityEntryStatus status,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime
    );
}