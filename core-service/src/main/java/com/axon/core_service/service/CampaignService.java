package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityRequest;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.core_service.repository.CampaignActivityRepository;
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
    private final CampaignActivityRepository campaignActivityRepository;
    private final CampaignActivityEntryRepository campaignActivityEntryRepository;

    public CampaignResponse createCampaign(CampaignRequest request) {
        Campaign campaign = new Campaign(request.getName());
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    public CampaignResponse updateCampaign(Long id, CampaignRequest request) {
        Campaign campaign = findCampaign(id);
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaign);
    }

    public List<CampaignResponse> getCampaigns() {
        return campaignRepository.findAll().stream()
                .map(CampaignResponse::from)
                .toList();
    }

    public CampaignResponse getCampaign(Long id) {
        return CampaignResponse.from(findCampaign(id));
    }

    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }

    public CampaignActivityResponse createCampaignActivity(Long campaignId, CampaignActivityRequest request) {
        Campaign campaign = findCampaign(campaignId);
        CampaignActivity campaignActivity = CampaignActivity.builder()
                .campaign(campaign)
                .name(request.getName())
                .limitCount(request.getLimitCount())
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .activityType(request.getActivityType())
                .build();
        CampaignActivity saved = campaignActivityRepository.save(campaignActivity);
        return CampaignActivityResponse.from(saved);
    }

    public CampaignActivityResponse updateCampaignActivity(Long campaignActivityId, CampaignActivityRequest request) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);
        campaignActivity.updateInfo(request.getName(), request.getLimitCount());
        validateStatusTransition(campaignActivity.getStatus(), request.getStatus());
        campaignActivity.changeStatus(request.getStatus());
        campaignActivity.changeDates(request.getStartDate(), request.getEndDate());
        return CampaignActivityResponse.from(campaignActivity);
    }

    public CampaignActivityResponse changeCampaignActivityStatus(Long campaignActivityId, CampaignActivityStatus status) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);
        validateStatusTransition(campaignActivity.getStatus(), status);
        campaignActivity.changeStatus(status);
        return CampaignActivityResponse.from(campaignActivity);
    }

    public List<CampaignActivityResponse> getCampaignActivities(Long campaignId) {
        return campaignActivityRepository.findAllByCampaign_Id(campaignId).stream()
                .map(CampaignActivityResponse::from)
                .toList();
    }

    public void deleteCampaignActivity(Long campaignActivityId) {
        campaignActivityRepository.deleteById(campaignActivityId);
    }

    public List<CampaignActivityResponse> getAllCampaignActivities() {
        return campaignActivityRepository.findAll().stream()
                .map(activity -> {
                    long participantCount = campaignActivityEntryRepository.countByCampaignActivity_Id(activity.getId());
                    return CampaignActivityResponse.from(activity, participantCount);
                })
                .toList();
    }

    public long getTotalCampaignActivityCount() {
        return campaignActivityRepository.count();
    }

    public CampaignActivityResponse getCampaignActivity(Long campaignActivityId) {
        CampaignActivity campaignActivity = findCampaignActivity(campaignActivityId);
        long participantCount = campaignActivityEntryRepository.countByCampaignActivity_Id(campaignActivityId);
        return CampaignActivityResponse.from(campaignActivity, participantCount);
    }

    public boolean isCampaignNameTaken(String name) {
        return campaignRepository.findByName(name).isPresent();
    }

    private void applyCampaignPolicies(Campaign campaign, CampaignRequest request) {
        campaign.updateBasicInfo(request.getName(), request.getTargetSegmentId());
        campaign.updateSchedule(request.getStartAt(), request.getEndAt());
        campaign.updateReward(request.getRewardType(), request.getRewardPayload());
    }

    private Campaign findCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign not found: " + id));
    }

    private CampaignActivity findCampaignActivity(Long id) {
        return campaignActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign activity not found: " + id));
    }

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
}
