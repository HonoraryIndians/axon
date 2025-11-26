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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
                    campaignActivity.getId(),
                    dto.getUserId(),
                    dto.getProductId(),
                    dto.occurredAt(),
                    PurchaseType.CAMPAIGNACTIVITY,
                    campaignActivity.getPrice(),
                    campaignActivity.getQuantity(),
                    requestedAt));
        }

        return saved;
    }
}