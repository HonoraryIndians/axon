package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignActivityRepository extends JpaRepository<CampaignActivity, Long> {

    /**
     * Retrieves all CampaignActivity records associated with the Campaign
     * identified by the given id.
     *
     * @param campaignId the id of the Campaign whose activities should be retrieved
     * @return a List of CampaignActivity belonging to the Campaign with the
     *         specified id; an empty list if none are found
     */
    List<CampaignActivity> findAllByCampaign_Id(Long campaignId);

    List<CampaignActivity> findAllByStatus(
            com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus status);

    /**
     * Finds campaigns that ended between the given time range and have the specified status.
     *
     * Used by CampaignStockSyncScheduler to find recently ended campaigns
     * that need stock synchronization.
     *
     * @param startTime start of the time range (inclusive)
     * @param endTime end of the time range (inclusive)
     * @param status the campaign activity status to filter by
     * @return list of campaigns that ended in the time range with the given status
     */
    List<CampaignActivity> findByEndDateBetweenAndStatus(LocalDateTime startTime, LocalDateTime endTime, CampaignActivityStatus status);
}