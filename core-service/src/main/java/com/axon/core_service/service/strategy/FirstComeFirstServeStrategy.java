package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.service.CampaignActivityEntryService;
import com.axon.core_service.service.batch.BatchStrategy;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeFirstServeStrategy implements BatchStrategy {

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
     * 배치 처리 (신규)
     *
     * 역할:
     * 1. 중복 메시지 제거 (같은 activityId + userId는 최신 것만)
     * 2. CampaignActivity bulk 조회
     * 3. Entry bulk upsert 위임
     */
    @Override
    public void processBatch(List<CampaignActivityKafkaProducerDto> messages) {
        if (messages.isEmpty()) {
            return;
        }

        log.info("Processing FCFS batch: {} messages", messages.size());

        // 1. 중복 제거: 같은 (activityId, userId)는 최신 메시지만 유지
        Map<String, CampaignActivityKafkaProducerDto> deduped = messages.stream()
                .collect(Collectors.toMap(
                        msg -> msg.getCampaignActivityId() + ":" + msg.getUserId(),
                        msg -> msg,
                        (existing, replacement) -> {
                            // 타임스탬프가 더 최신인 것 선택
                            Long existingTs = existing.getTimestamp();
                            Long replacementTs = replacement.getTimestamp();
                            if (existingTs == null) return replacement;
                            if (replacementTs == null) return existing;
                            return replacementTs > existingTs ? replacement : existing;
                        }
                ));

        List<CampaignActivityKafkaProducerDto> dedupedMessages = List.copyOf(deduped.values());

        log.info("After deduplication: {} messages (removed {} duplicates)",
                dedupedMessages.size(), messages.size() - dedupedMessages.size());

        // 2. 필요한 CampaignActivity ID 추출
        Set<Long> activityIds = dedupedMessages.stream()
                .map(CampaignActivityKafkaProducerDto::getCampaignActivityId)
                .collect(Collectors.toSet());

        // 3. CampaignActivity bulk 조회 (1회 DB 접근)
        Map<Long, CampaignActivity> activityMap = campaignActivityRepository
                .findAllById(activityIds)
                .stream()
                .collect(Collectors.toMap(CampaignActivity::getId, activity -> activity));

        // 4. 존재하지 않는 activity 검증
        Set<Long> missingIds = activityIds.stream()
                .filter(id -> !activityMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            log.warn("존재하지 않는 캠페인 활동 ID: {}", missingIds);
        }

        // 5. Entry bulk upsert
        List<CampaignActivityKafkaProducerDto> validMessages = dedupedMessages.stream()
                .filter(msg -> activityMap.containsKey(msg.getCampaignActivityId()))
                .toList();

        List<CampaignActivityKafkaProducerDto> invalidMessages = dedupedMessages.stream()
                .filter(msg -> !activityMap.containsKey(msg.getCampaignActivityId()))
                .toList();

        if (!invalidMessages.isEmpty()) {
            log.warn("⚠️ [FCFS] Skipping {} messages with invalid activityIds: {}",
                invalidMessages.size(),
                invalidMessages.stream()
                    .map(CampaignActivityKafkaProducerDto::getCampaignActivityId)
                    .distinct()
                    .collect(Collectors.toList()));
        }

        if (!validMessages.isEmpty()) {
            log.info("✅ [FCFS] Processing {} valid messages (users: {})",
                validMessages.size(),
                validMessages.stream()
                    .map(CampaignActivityKafkaProducerDto::getUserId)
                    .limit(10)
                    .collect(Collectors.toList()));
            campaignActivityEntryService.upsertBatch(
                    activityMap,
                    validMessages,
                    CampaignActivityEntryStatus.APPROVED
            );
        }
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