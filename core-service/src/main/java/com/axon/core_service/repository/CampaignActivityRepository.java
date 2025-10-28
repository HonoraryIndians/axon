package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignActivityRepository extends JpaRepository<CampaignActivity, Long> {

    List<CampaignActivity> findAllByCampaign_Id(Long campaignId);
}
