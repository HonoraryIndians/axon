package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.repository.CampaignRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CampaignService {

    private final CampaignRepository campaignRepository;

    /**
     * Creates a new Campaign from the provided request, persists it, and returns a CampaignResponse.
     *
     * @param request the data used to initialize and configure the new campaign (name, schedule, reward, etc.)
     * @return a CampaignResponse representing the persisted campaign
     */
    public CampaignResponse createCampaign(CampaignRequest request) {
        Campaign campaign = new Campaign(request.getName());
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    /**
     * Update an existing campaign using values from the provided request.
     *
     * @param id      the identifier of the campaign to update
     * @param request the new campaign data to apply
     * @return        the updated campaign represented as a CampaignResponse
     * @throws IllegalArgumentException if no campaign exists with the given id
     */
    public CampaignResponse updateCampaign(Long id, CampaignRequest request) {
        Campaign campaign = findCampaign(id);
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaign);
    }

    /**
     * Retrieves all stored campaigns and maps each to a CampaignResponse DTO.
     *
     * @return a list of CampaignResponse objects representing every campaign in the repository
     */
    public List<CampaignResponse> getCampaigns() {
        return campaignRepository.findAll().stream()
                .map(CampaignResponse::from)
                .toList();
    }

    /**
     * Retrieve a campaign by its identifier.
     *
     * @param id the campaign identifier
     * @return the campaign represented as a CampaignResponse
     */
    public CampaignResponse getCampaign(Long id) {
        return CampaignResponse.from(findCampaign(id));
    }

    /**
     * Deletes the campaign with the given id.
     *
     * @param id the id of the campaign to delete
     */
    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }

    /**
     * Checks whether a campaign with the given name already exists.
     *
     * @param name the campaign name to check
     * @return `true` if a campaign with the specified name exists, `false` otherwise
     */
    public boolean isCampaignNameTaken(String name) {
        return campaignRepository.findByName(name).isPresent();
    }

    /**
     * Apply name, target segment, schedule, and reward values from the request to the given campaign.
     *
     * @param campaign the Campaign to update
     * @param request  the source of new name, target segment, schedule, and reward values
     */
    private void applyCampaignPolicies(Campaign campaign, CampaignRequest request) {
        campaign.updateBasicInfo(request.getName(), request.getTargetSegmentId());
        campaign.updateSchedule(request.getStartAt(), request.getEndAt());
        campaign.updateReward(request.getRewardType(), request.getRewardPayload());
    }

    /**
     * Retrieves the Campaign with the given id.
     *
     * @param id the campaign identifier
     * @return the Campaign with the specified id
     * @throws IllegalArgumentException if no campaign with the given id exists
     */
    private Campaign findCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign not found: " + id));
    }

}