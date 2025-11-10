package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityRequest;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.service.CampaignActivityService;
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

@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
@Slf4j
public class CampaignActivityController {

    private final CampaignActivityService campaignActivityService;

    @PostMapping("/{campaignId}/activities")
    public ResponseEntity<CampaignActivityResponse> createCampaignActivity(@PathVariable Long campaignId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignActivityService.createCampaignActivity(campaignId, request));
    }

    @GetMapping("/{campaignId}/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignActivityService.getCampaignActivities(campaignId));
    }

    @PutMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> updateCampaignActivity(@PathVariable Long campaignActivityId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignActivityService.updateCampaignActivity(campaignActivityId, request));
    }

    @PatchMapping("/activities/{campaignActivityId}/status")
    public ResponseEntity<CampaignActivityResponse> changeCampaignActivityStatus(@PathVariable Long campaignActivityId,
                                                                                 @RequestParam CampaignActivityStatus status) {
        return ResponseEntity.ok(campaignActivityService.changeCampaignActivityStatus(campaignActivityId, status));
    }

    @DeleteMapping("/activities/{campaignActivityId}")
    public ResponseEntity<Void> deleteCampaignActivity(@PathVariable Long campaignActivityId) {
        campaignActivityService.deleteCampaignActivity(campaignActivityId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities() {
        return ResponseEntity.ok(campaignActivityService.getAllCampaignActivities());
    }

    @GetMapping("/activities/count")
    public ResponseEntity<Long> getTotalCampaignActivityCount() {
        return ResponseEntity.ok(campaignActivityService.getTotalCampaignActivityCount());
    }

    @GetMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> getCampaignActivity(@PathVariable Long campaignActivityId) {
        log.info("Fetching campaign activity with ID: {}", campaignActivityId);
        return ResponseEntity.ok(campaignActivityService.getCampaignActivity(campaignActivityId));
    }
}
