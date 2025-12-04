package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityRequest;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.CampaignRepository;
import java.util.List;

import com.axon.core_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CampaignActivityService {

    private final CampaignRepository campaignRepository;
    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryRepository campaignActivityEntryRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Create a new CampaignActivity associated with the given campaign.
     *
     * @param campaignId the ID of the campaign to associate the new activity with
     * @param request    the request containing the activity's properties (name,
     *                   limits, status, dates, type, filters)
     * @return a {@code CampaignActivityResponse} representing the persisted
     *         campaign activity
     * @throws IllegalArgumentException if no campaign exists with the given
     *                                  {@code campaignId}
     */
    public CampaignActivityResponse createCampaignActivity(Long campaignId, CampaignActivityRequest request) {
        Campaign campaign = findCampaign(campaignId);
        Product product = null;
        if (request.getProductId() != null) {
            product = findProduct(request.getProductId());
        }
        CampaignActivity campaignActivity = CampaignActivity.builder()
                .campaign(campaign)
                .name(request.getName())
                .limitCount(request.getLimitCount())
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .activityType(request.getActivityType())
                .filters(request.getFilters())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .product(product)
                .imageUrl(request.getImageUrl())
                .budget(request.getBudget())
                .build();
        CampaignActivity saved = campaignActivityRepository.save(campaignActivity);
        return CampaignActivityResponse.from(saved);
    }

    /**
     * Updates core attributes, status, dates, and optional filters of an existing
     * CampaignActivity.
     *
     * @param campaignActivityId the id of the CampaignActivity to update
     * @param request            the new values for name, limit count, status,
     *                           start/end dates and optional filters
     * @return a response object representing the updated CampaignActivity
     * @throws IllegalArgumentException if no CampaignActivity exists with the given
     *                                  id
     * @throws IllegalStateException    if the requested status transition is not
     *                                  allowed
     */
    public CampaignActivityResponse updateCampaignActivity(Long campaignActivityId, CampaignActivityRequest request) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);

        // Update Basic Info
        campaignActivity.updateInfo(request.getName(), request.getLimitCount());

        // Update Campaign if changed
        if (request.getCampaignId() != null && !request.getCampaignId().equals(campaignActivity.getCampaignId())) {
            Campaign newCampaign = findCampaign(request.getCampaignId());
            campaignActivity.assignCampaign(newCampaign);
        }

        // Update Status
        validateStatusTransition(campaignActivity.getStatus(), request.getStatus());
        campaignActivity.changeStatus(request.getStatus());

        // Update Dates
        campaignActivity.changeDates(request.getStartDate(), request.getEndDate());

        // Update Activity Type
        campaignActivity.updateActivityType(request.getActivityType());

        // Update Filters
        if (request.getFilters() != null) {
            campaignActivity.setFilters(request.getFilters());
        }

        // Update Product Info
        Product newProduct = null;
        if (request.getProductId() != null) {
            newProduct = findProduct(request.getProductId());
        }
        campaignActivity.updateProductInfo(newProduct, request.getPrice(), request.getQuantity());

        // Update Image URL
        campaignActivity.updateImageUrl(request.getImageUrl());

        // Update Budget
        campaignActivity.updateBudget(request.getBudget());

        // Invalidate Cache
        evictMetaCache(campaignActivityId);

        return CampaignActivityResponse.from(campaignActivity);
    }

    /**
     * Change the status of the campaign activity identified by the given ID.
     *
     * @param campaignActivityId the ID of the campaign activity to update
     * @param status             the new status to apply
     * @return a response representing the updated campaign activity
     * @throws IllegalArgumentException if no campaign activity exists for the
     *                                  provided ID
     * @throws IllegalStateException    if the status transition from the current
     *                                  status to the requested status is not
     *                                  allowed
     */
    public CampaignActivityResponse changeCampaignActivityStatus(Long campaignActivityId,
            CampaignActivityStatus status) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);
        validateStatusTransition(campaignActivity.getStatus(), status);
        campaignActivity.changeStatus(status);

        // Invalidate Cache
        evictMetaCache(campaignActivityId);

        return CampaignActivityResponse.from(campaignActivity);
    }

    /**
     * Retrieve all campaign activities for the given campaign.
     *
     * @param campaignId the ID of the campaign whose activities to retrieve
     * @return a list of {@code CampaignActivityResponse} representing activities
     *         belonging to the campaign
     */
    public List<CampaignActivityResponse> getCampaignActivities(Long campaignId) {
        return campaignActivityRepository.findAllByCampaign_Id(campaignId).stream()
                .map(activity -> {
                    long participantCount = campaignActivityEntryRepository
                            .countByCampaignActivity_Id(activity.getId());
                    return CampaignActivityResponse.from(activity, participantCount);
                })
                .toList();
    }

    /**
     * Deletes the CampaignActivity identified by the given id.
     *
     * @param campaignActivityId the id of the CampaignActivity to delete
     */
    public void deleteCampaignActivity(Long campaignActivityId) {
        campaignActivityRepository.deleteById(campaignActivityId);
        evictMetaCache(campaignActivityId);
    }

    /**
     * Retrieve all campaign activities with their participant counts.
     *
     * Each returned response contains the activity data and the number of
     * associated participants.
     *
     * @return a list of CampaignActivityResponse objects, each including the
     *         activity and its participant count
     */
    public List<CampaignActivityResponse> getAllCampaignActivities() {
        return campaignActivityRepository.findAll().stream()
                .map(activity -> {
                    long participantCount = campaignActivityEntryRepository
                            .countByCampaignActivity_Id(activity.getId());
                    return CampaignActivityResponse.from(activity, participantCount);
                })
                .toList();
    }

    /**
     * Retrieves the total number of campaign activities.
     *
     * @return the total number of CampaignActivity records
     */
    public long getTotalCampaignActivityCount() {
        return campaignActivityRepository.count();
    }

    /**
     * Fetches a campaign activity by its ID and includes the activity's participant
     * count.
     *
     * @param campaignActivityId the ID of the campaign activity to retrieve
     * @return a CampaignActivityResponse representing the activity and its
     *         participant count
     */
    public CampaignActivityResponse getCampaignActivity(Long campaignActivityId) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);
        long participantCount = campaignActivityEntryRepository.countByCampaignActivity_Id(campaignActivityId);
        return CampaignActivityResponse.from(campaignActivity, participantCount);
    }

    /**
     * Retrieve a Campaign by its id.
     *
     * @param id the campaign id
     * @return the Campaign with the given id
     * @throws IllegalArgumentException if no Campaign exists with the given id
     */
    private Campaign findCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign not found: " + id));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("product not found: " + id));
    }

    /**
     * Retrieve the CampaignActivity with the given ID.
     *
     * @param id the campaign activity identifier
     * @return the CampaignActivity matching the provided ID
     * @throws IllegalArgumentException if no CampaignActivity exists for the given
     *                                  ID
     */
    private CampaignActivity findCampaignActivity(Long id) {
        return campaignActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign activity not found: " + id));
    }

    /**
     * Validates that changing a campaign activity from `current` to `next` is
     * permitted.
     *
     * Allowed transitions:
     * - same status (no change)
     * - DRAFT -> ACTIVE
     * - ACTIVE -> PAUSED
     * - ACTIVE -> ENDED
     * - PAUSED -> ACTIVE
     *
     * @param current the current campaign activity status
     * @param next    the requested campaign activity status
     * @throws IllegalStateException if the transition from `current` to `next` is
     *                               not allowed
     */
    private void validateStatusTransition(CampaignActivityStatus current, CampaignActivityStatus next) {
        if (current == next) {
            return;
        }
        if (current == CampaignActivityStatus.DRAFT && next == CampaignActivityStatus.ACTIVE) {
            return;
        }
        if (current == CampaignActivityStatus.ACTIVE
                && (next == CampaignActivityStatus.PAUSED || next == CampaignActivityStatus.ENDED)) {
            return;
        }
        if (current == CampaignActivityStatus.PAUSED && next == CampaignActivityStatus.ACTIVE) {
            return;
        }
        throw new IllegalStateException("invalid status transition: " + current + " -> " + next);
    }

    private void evictMetaCache(Long campaignActivityId) {
        String key = "campaign:%s:meta".formatted(campaignActivityId);
        redisTemplate.delete(key);
    }
}