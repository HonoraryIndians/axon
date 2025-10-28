package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignActivityEntryRepository extends JpaRepository<CampaignActivityEntry, Long> {

    Optional<CampaignActivityEntry> findByCampaignActivity_IdAndUserId(Long campaignActivityId, Long userId);

    Page<CampaignActivityEntry> findByCampaignActivity_Id(Long campaignActivityId, Pageable pageable);

    Page<CampaignActivityEntry> findByCampaignActivity_IdAndStatus(Long campaignActivityId,
                                                                   CampaignActivityEntryStatus status,
                                                                   Pageable pageable);

    long countByCampaignActivity_Id(Long campaignActivityId);
}
