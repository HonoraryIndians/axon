package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.service.CampaignActivityEntryService;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeFirstServeStrategy implements CampaignStrategy {

    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryService campaignActivityEntryService;

    /**
     * Processes a first-come-first-serve campaign event by locating the campaign activity and creating or updating an approved entry for the user.
     *
     * @param eventDto the incoming campaign activity event containing the campaign activity ID and user information
     * @throws IllegalArgumentException if no campaign activity exists with the provided ID
     */
    @Override
    public void process(CampaignActivityKafkaProducerDto eventDto) {
        CampaignActivity campaignActivity = campaignActivityRepository.findById(eventDto.getCampaignActivityId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 캠페인 활동입니다. ID: " + eventDto.getCampaignActivityId()));

        log.info("선착순 확정 메시지 처리: CampaignActivity={} User={}", eventDto.getCampaignActivityId(), eventDto.getUserId());
        campaignActivityEntryService.upsertEntry(campaignActivity, eventDto, CampaignActivityEntryStatus.APPROVED, true);
    }

    /**
     * Identifies the campaign activity type handled by this strategy.
     *
     * @return the campaign activity type `CampaignActivityType.FIRST_COME_FIRST_SERVE`
     */
    @Override
    public CampaignActivityType getType() {
        return CampaignActivityType.FIRST_COME_FIRST_SERVE;
    }
}