package com.axon.core_service.service.scheduler;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that syncs campaign product stock after campaigns end.
 *
 * For FCFS campaigns, stock is managed by Redis counter during the campaign.
 * This scheduler periodically checks for recently ended campaigns and syncs
 * their Product.stock from Redis counter to MySQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignStockSyncScheduler {

    private final CampaignActivityRepository campaignActivityRepository;
    private final ProductService productService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Runs every 5 minutes to sync stock for ended campaigns.
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    @Transactional
    public void syncEndedCampaignStocks() {
        log.debug("Checking for ended campaigns to sync stock...");

        // Find campaigns that ended in the last 10 minutes and are still ACTIVE
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        LocalDateTime now = LocalDateTime.now();

        List<CampaignActivity> endedCampaigns = campaignActivityRepository
                .findByEndDateBetweenAndStatus(tenMinutesAgo, now, CampaignActivityStatus.ACTIVE);

        if (endedCampaigns.isEmpty()) {
            log.debug("No ended campaigns found");
            return;
        }

        log.info("Found {} ended campaigns to sync", endedCampaigns.size());

        for (CampaignActivity campaign : endedCampaigns) {
            try {
                syncCampaignStock(campaign);
            } catch (Exception e) {
                log.error("Failed to sync stock for campaign {}: {}",
                    campaign.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Syncs a single campaign's stock from Redis to MySQL.
     */
    private void syncCampaignStock(CampaignActivity campaign) {
        String counterKey = "campaign:" + campaign.getId() + ":counter";

        // Get sold count from Redis
        String soldCountStr = redisTemplate.opsForValue().get(counterKey);
        Long soldCount = soldCountStr != null ? Long.parseLong(soldCountStr) : 0L;

        log.info("Syncing campaign {}: soldCount={}, limit={}",
            campaign.getId(), soldCount, campaign.getLimitCount());

        // Sync to MySQL Product.stock
        if (campaign.getProductId() != null) {
            productService.syncCampaignStock(campaign.getProductId(), soldCount);
        }

        // Update campaign status to ENDED
        campaign.updateStatus(CampaignActivityStatus.ENDED);
        campaignActivityRepository.save(campaign);

        log.info("Campaign {} stock synced and marked as ENDED", campaign.getId());
    }

    /**
     * Manual sync endpoint (for testing or admin use).
     * Can be called via REST API if needed.
     */
    @Transactional
    public void syncCampaignStockManually(Long campaignActivityId) {
        CampaignActivity campaign = campaignActivityRepository.findById(campaignActivityId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignActivityId));

        log.info("Manual sync requested for campaign {}", campaignActivityId);
        syncCampaignStock(campaign);
    }
}
