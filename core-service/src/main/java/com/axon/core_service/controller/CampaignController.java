package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityRequest;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.service.CampaignService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns() {
        return ResponseEntity.ok(campaignService.getCampaigns());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@PathVariable Long id,
                                                           @RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{campaignId}/activities")
    public ResponseEntity<CampaignActivityResponse> createCampaignActivity(@PathVariable Long campaignId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignService.createCampaignActivity(campaignId, request));
    }

    @GetMapping("/{campaignId}/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignService.getCampaignActivities(campaignId));
    }

    @PutMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> updateCampaignActivity(@PathVariable Long campaignActivityId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaignActivity(campaignActivityId, request));
    }

    @PatchMapping("/activities/{campaignActivityId}/status")
    public ResponseEntity<CampaignActivityResponse> changeCampaignActivityStatus(@PathVariable Long campaignActivityId,
                                                                                 @RequestParam CampaignActivityStatus status) {
        return ResponseEntity.ok(campaignService.changeCampaignActivityStatus(campaignActivityId, status));
    }

    @DeleteMapping("/activities/{campaignActivityId}")
    public ResponseEntity<Void> deleteCampaignActivity(@PathVariable Long campaignActivityId) {
        campaignService.deleteCampaignActivity(campaignActivityId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities() {
        return ResponseEntity.ok(campaignService.getAllCampaignActivities());
    }

    @GetMapping("/activities/count")
    public ResponseEntity<Long> getTotalCampaignActivityCount() {
        return ResponseEntity.ok(campaignService.getTotalCampaignActivityCount());
    }

    @GetMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> getCampaignActivity(@PathVariable Long campaignActivityId) {
        return ResponseEntity.ok(campaignService.getCampaignActivity(campaignActivityId));
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkCampaignName(@RequestParam String name) {
        return ResponseEntity.ok(campaignService.isCampaignNameTaken(name));
    }
}
