package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.service.CampaignActivityEntryService;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.KafkaProducerDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeFirstServeStrategy implements CampaignStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryService campaignActivityEntryService;

    @Override
    public void process(KafkaProducerDto eventDto) {
        CampaignActivity campaignActivity = campaignActivityRepository.findById(eventDto.getCampaignActivityId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 캠페인 활동입니다. ID: " + eventDto.getCampaignActivityId()));

        int limit = Optional.ofNullable(campaignActivity.getLimitCount()).orElse(Integer.MAX_VALUE);
        String eventKey = "campaign-activity:" + eventDto.getCampaignActivityId();
        String userKey = String.valueOf(eventDto.getUserId());

        Long addResult = redisTemplate.opsForSet().add(eventKey, userKey);
        boolean firstHit = addResult != null && addResult == 1L;

        if (!firstHit) {
            log.info("중복 응모입니다. CampaignActivity: {}, User: {}", eventDto.getCampaignActivityId(), eventDto.getUserId());
            campaignActivityEntryService.upsertEntry(campaignActivity, eventDto, CampaignActivityEntryStatus.DUPLICATED, true);
            return;
        }

        Long currentEntries = redisTemplate.opsForSet().size(eventKey);
        boolean withinLimit = currentEntries != null && currentEntries <= limit;

        if (withinLimit) {
            log.info("선착순 성공! CampaignActivity: {}, User: {}, 현재 인원: {}/{}", eventDto.getCampaignActivityId(), eventDto.getUserId(), currentEntries, limit);
            campaignActivityEntryService.upsertEntry(campaignActivity, eventDto, CampaignActivityEntryStatus.APPROVED, true);
        } else {
            log.info("선착순 마감. CampaignActivity: {}, User: {}", eventDto.getCampaignActivityId(), eventDto.getUserId());
            campaignActivityEntryService.upsertEntry(campaignActivity, eventDto, CampaignActivityEntryStatus.REJECTED, true);
        }
    }

    @Override
    public CampaignActivityType getType() {
        return CampaignActivityType.FIRST_COME_FIRST_SERVE;
    }
}
