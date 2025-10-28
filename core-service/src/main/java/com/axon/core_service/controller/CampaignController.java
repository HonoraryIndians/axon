package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService campaignService;

    // JSON형식의 요청을 받으면 아래 함수를 통해 값을 dto로 변환하여 처리합니다.

    // 새 캠페인을 등록하며 한도·일정·보상 정책을 전달한다.
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    // 전체 캠페인을 조회한다.
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns() {

        return ResponseEntity.ok(campaignService.getCampaigns());
    }

    // 단일 캠페인의 상세 정보를 확인한다.
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    // 캠페인의 기본 정보·정책을 수정한다.
    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@PathVariable Long id, @RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    // 캠페인을 삭제한다.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    // 캠페인 이름 중복을 확인한다.
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkCampaignName(@RequestParam String name) {
        return ResponseEntity.ok(campaignService.isCampaignNameTaken(name));
    }
}
