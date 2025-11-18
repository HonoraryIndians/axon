package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.core_service.repository.EventOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CampaignMetricsService {
    private final CampaignActivityEntryRepository entryRepository;
    private final EventOccurrenceRepository eventOccurrenceRepository;
    public Long getApprovedCount(Long activityId, LocalDateTime start, LocalDateTime end) {
        return entryRepository.countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
                activityId,
                CampaignActivityEntryStatus.APPROVED,
                start,
                end
        );
    }

}
