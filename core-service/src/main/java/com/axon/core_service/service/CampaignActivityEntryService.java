package com.axon.core_service.service;

import com.axon.core_service.aop.DistributedLock;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.domain.purchase.PurchaseType;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class CampaignActivityEntryService {

    private final CampaignActivityEntryRepository campaignActivityEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Upserts a CampaignActivityEntry for the given campaign activity and DTO,
     * persists it, and emits an approval event when the entry is approved for a
     * purchase-related activity.
     *
     * @param campaignActivity the campaign activity associated with the entry
     * @param dto              source data containing the user ID, product ID, and
     *                         an optional epoch-millisecond timestamp
     * @param nextStatus       the status to set on the entry
     * @param processed        if true, marks the entry as processed at the current
     *                         time
     * @return the persisted CampaignActivityEntry
     */
    @DistributedLock(key = "'lock:entry:' + #campaignActivity.id + ':' + #dto.userId", waitTime = 3, leaseTime = 5)
    @Transactional
    public CampaignActivityEntry upsertEntry(CampaignActivity campaignActivity,
            CampaignActivityKafkaProducerDto dto,
            CampaignActivityEntryStatus nextStatus,
            boolean processed) {
        return upsertEntryInternal(campaignActivity, dto, nextStatus, processed);
    }

    private CampaignActivityEntry upsertEntryInternal(CampaignActivity campaignActivity,
            CampaignActivityKafkaProducerDto dto,
            CampaignActivityEntryStatus nextStatus,
            boolean processed) {
        Instant requestedAt = Optional.ofNullable(dto.getTimestamp())
                .map(Instant::ofEpochMilli)
                .orElseGet(Instant::now);

        CampaignActivityEntry entry = campaignActivityEntryRepository
                .findByCampaignActivity_IdAndUserId(campaignActivity.getId(), dto.getUserId())
                .orElseGet(() -> CampaignActivityEntry.create(
                        campaignActivity,
                        dto.getUserId(),
                        dto.getProductId(),
                        requestedAt));

        entry.updateProduct(dto.getProductId());
        entry.updateStatus(nextStatus);
        if (processed) {
            entry.markProcessedNow();
        }

        CampaignActivityEntry saved = campaignActivityEntryRepository.save(entry);

        if (nextStatus == CampaignActivityEntryStatus.APPROVED
                && campaignActivity.getActivityType().isPurchaseRelated()) {
            eventPublisher.publishEvent(new PurchaseInfoDto(
                    campaignActivity.getCampaignId(),
                    campaignActivity.getId(),
                    dto.getUserId(),
                    dto.getProductId(),
                    dto.occurredAt(),
                    PurchaseType.CAMPAIGNACTIVITY,
                    campaignActivity.getPrice(),
                    (int) (dto.getQuantity() != null ? dto.getQuantity().longValue() : 1L),
                    requestedAt));
        }

        return saved;
    }
    /**
     * Bulk upsert (신규)
     *
     * 역할:
     * 1. 기존 Entry bulk 조회
     * 2. 신규/업데이트 Entry 준비
     * 3. Bulk save
     * 4. Bulk event 발행
     *
     * @param activityMap activityId -> CampaignActivity 맵
     * @param messages 처리할 메시지 리스트
     * @param status 설정할 상태
     */
    @Transactional
    public void upsertBatch(
            Map<Long, CampaignActivity> activityMap,
            List<CampaignActivityKafkaProducerDto> messages,
            CampaignActivityEntryStatus status) {

        if (messages.isEmpty()) {
            return;
        }

        log.info("Bulk upsert: {} entries", messages.size());

        // 1. activityId, userId 추출
        List<Long> activityIds = messages.stream()
                .map(CampaignActivityKafkaProducerDto::getCampaignActivityId)
                .distinct()
                .toList();

        List<Long> userIds = messages.stream()
                .map(CampaignActivityKafkaProducerDto::getUserId)
                .distinct()
                .toList();

        // 2. 기존 Entry bulk 조회 (1회 DB 접근)
        List<CampaignActivityEntry> existingEntries = campaignActivityEntryRepository.findByActivityIdsAndUserIds(activityIds, userIds);

        // 3. 기존 Entry를 Map으로 변환 (빠른 조회용)
        Map<String, CampaignActivityEntry> existingMap = existingEntries.stream()
                .collect(Collectors.toMap(
                        entry -> entry.getCampaignActivity().getId() + ":" + entry.getUserId(),
                        entry -> entry
                ));

        // 4. 저장할 Entry 리스트 + 발행할 이벤트 리스트 준비
        List<CampaignActivityEntry> toSave = new ArrayList<>();
        List<PurchaseInfoDto> purchaseEvents = new ArrayList<>();

        for (CampaignActivityKafkaProducerDto dto : messages) {
            CampaignActivity activity = activityMap.get(dto.getCampaignActivityId());
            if (activity == null) {
                continue;  // 이미 Strategy에서 필터링 됨
            }

            String key = activity.getId() + ":" + dto.getUserId();
            Instant requestedAt = Optional.ofNullable(dto.getTimestamp())
                    .map(Instant::ofEpochMilli)
                    .orElseGet(Instant::now);

            // 기존 Entry 있으면 업데이트, 없으면 신규 생성
            CampaignActivityEntry entry = existingMap.getOrDefault(key,
                    CampaignActivityEntry.create(
                            activity,
                            dto.getUserId(),
                            dto.getProductId(),
                            requestedAt
                    ));

            entry.updateProduct(dto.getProductId());
            entry.updateStatus(status);
            entry.markProcessedNow();
            toSave.add(entry);

            // 구매 관련 활동이면 이벤트 준비
            boolean isApproved = (status == CampaignActivityEntryStatus.APPROVED);
            boolean isPurchaseRelated = activity.getActivityType().isPurchaseRelated();
            boolean isNewEntry = !existingMap.containsKey(key);  // 새로운 Entry인지 확인

            log.info("Checking event condition: userId={}, status={}, type={}, isApproved={}, isPurchaseRelated={}, isNewEntry={}",
                    dto.getUserId(), status, activity.getActivityType(), isApproved, isPurchaseRelated, isNewEntry);

            if (isApproved && isPurchaseRelated && isNewEntry) {  // isNewEntry 조건 추가
                log.info("Adding purchase event for NEW entry userId={}", dto.getUserId());
                purchaseEvents.add(new PurchaseInfoDto(
                        activity.getCampaignId(),
                        activity.getId(),
                        dto.getUserId(),
                        dto.getProductId(),
                        dto.occurredAt(),
                        PurchaseType.CAMPAIGNACTIVITY,
                        activity.getPrice(),
                        (int) (dto.getQuantity() != null ? dto.getQuantity().longValue() : 1L),
                        requestedAt
                ));
            }
        }

        // 5. Bulk save (1회 DB 접근)
        log.info("📝 [Entry] Attempting to save {} entries (users: {})",
            toSave.size(),
            toSave.stream().map(e -> e.getUserId()).limit(10).collect(Collectors.toList()));

        if (!toSave.isEmpty()) {
            try {
                campaignActivityEntryRepository.saveAll(toSave);
                log.info("✅ [Entry] Saved {} entries successfully", toSave.size());
            } catch (DataIntegrityViolationException e) {
                // Handle duplicates gracefully - process individually
                log.warn("⚠️ [Entry] Duplicate entries detected in batch, processing individually");
                int saved = 0;
                for (CampaignActivityEntry entry : toSave) {
                    try {
                        campaignActivityEntryRepository.save(entry);
                        saved++;
                    } catch (DataIntegrityViolationException ex) {
                        log.debug("Skipping duplicate entry: activity={}, user={}",
                            entry.getCampaignActivity().getId(), entry.getUserId());
                    }
                }
                log.info("✅ [Entry] Saved {} entries ({} duplicates skipped)", saved, toSave.size() - saved);
            }
        }

        // 6. Bulk event 발행
        if (!purchaseEvents.isEmpty()) {
            log.info("📢 [Purchase Event] Publishing {} events for users: {}",
                purchaseEvents.size(),
                purchaseEvents.stream().map(PurchaseInfoDto::userId).limit(10).collect(Collectors.toList()));
            purchaseEvents.forEach(eventPublisher::publishEvent);
        }
    }


}