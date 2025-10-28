package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.messaging.dto.KafkaProducerDto;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class CampaignActivityEntryService {

    private final CampaignActivityEntryRepository campaignActivityEntryRepository;
    private final ProductService productService;

    public CampaignActivityEntry upsertEntry(CampaignActivity campaignActivity,
                                            KafkaProducerDto dto,
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
                        requestedAt
                ));

        entry.updateProduct(dto.getProductId());
        entry.updateStatus(nextStatus);
        if (processed) {
            entry.markProcessedNow();
        }

        if (nextStatus == CampaignActivityEntryStatus.APPROVED) {
            productService.decreaseStock(dto.getProductId());
        }

        return campaignActivityEntryRepository.save(entry);
    }
}
