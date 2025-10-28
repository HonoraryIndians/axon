package com.axon.core_service.service;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CampaignService {
    private final CampaignRepository campaignRepository;

    // 새 캠페인을 생성하면서 한도·일정·보상 정책을 함께 세팅한다.
    public CampaignResponse createCampaign(CampaignRequest request) {
        Campaign campaign = new Campaign(request.getName());
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    // 캠페인의 기본 정보와 정책을 수정하고 상태 전환을 검증한다.
    public CampaignResponse updateCampaign(Long id, CampaignRequest request) {
        Campaign campaign = findCampaign(id);
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaign);
    }


    // 모든 캠페인을 한 번에 조회한다.
    public List<CampaignResponse> getCampaigns() {
        return campaignRepository.findAll().stream()
                .map(CampaignResponse::from)
                .toList();
    }

    // 단일 캠페인 상세 정보를 조회한다.
    public CampaignResponse getCampaign(Long id) {
        return CampaignResponse.from(findCampaign(id));
    }

    // 캠페인과 연관 이벤트를 제거한다.
    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }


    // 캠페인 이름이 이미 사용 중인지 확인한다.
    public boolean isCampaignNameTaken(String name) {
        return campaignRepository.findByName(name).isPresent();
    }

    // 캠페인의 핵심 정책(기본 정보·일정·보상)을 일괄 적용한다。
    private void applyCampaignPolicies(Campaign campaign, CampaignRequest request) {
        campaign.updateBasicInfo(request.getName(), request.getTargetSegmentId());
        campaign.updateSchedule(request.getStartAt(), request.getEndAt());
        campaign.updateReward(request.getRewardType(), request.getRewardPayload());
    }

    // 존재하는 캠페인인지 확인하고 엔티티를 반환한다.
    public Campaign findCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign not found: " + id));
    }

}
