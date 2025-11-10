package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignActivityRepository extends JpaRepository<CampaignActivity, Long> {

    /**
 * Retrieves all CampaignActivity records associated with the Campaign identified by the given id.
 *
 * @param campaignId the id of the Campaign whose activities should be retrieved
 * @return a List of CampaignActivity belonging to the Campaign with the specified id; an empty list if none are found
 */
List<CampaignActivity> findAllByCampaign_Id(Long campaignId);
}