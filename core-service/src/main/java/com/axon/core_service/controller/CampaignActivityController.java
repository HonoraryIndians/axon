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

    /**
     * Create a new activity for the specified campaign.
     *
     * @param campaignId the ID of the campaign to attach the new activity to
     * @param request the details of the campaign activity to create
     * @return the created CampaignActivityResponse
     */
    @PostMapping("/{campaignId}/activities")
    public ResponseEntity<CampaignActivityResponse> createCampaignActivity(@PathVariable Long campaignId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignActivityService.createCampaignActivity(campaignId, request));
    }

    /**
     * Retrieve all activities for a specific campaign.
     *
     * @param campaignId the ID of the campaign whose activities are being retrieved
     * @return a list of CampaignActivityResponse objects for the specified campaign
     */
    @GetMapping("/{campaignId}/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignActivityService.getCampaignActivities(campaignId));
    }

    /**
     * Updates an existing campaign activity with the supplied request data.
     *
     * @param campaignActivityId the ID of the campaign activity to update
     * @param request             the new values for the campaign activity
     * @return                    the updated campaign activity representation
     */
    @PutMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> updateCampaignActivity(@PathVariable Long campaignActivityId,
                                                                           @RequestBody @Valid CampaignActivityRequest request) {
        return ResponseEntity.ok(campaignActivityService.updateCampaignActivity(campaignActivityId, request));
    }

    /**
     * Changes the status of the campaign activity identified by the given ID.
     *
     * @param campaignActivityId the ID of the campaign activity to update
     * @param status the new status to set for the campaign activity
     * @return the updated CampaignActivityResponse
     */
    @PatchMapping("/activities/{campaignActivityId}/status")
    public ResponseEntity<CampaignActivityResponse> changeCampaignActivityStatus(@PathVariable Long campaignActivityId,
                                                                                 @RequestParam CampaignActivityStatus status) {
        return ResponseEntity.ok(campaignActivityService.changeCampaignActivityStatus(campaignActivityId, status));
    }

    /**
     * Delete the campaign activity identified by the given ID.
     *
     * @param campaignActivityId the ID of the campaign activity to delete
     * @return a response entity with no content (HTTP 204) indicating the activity was deleted
     */
    @DeleteMapping("/activities/{campaignActivityId}")
    public ResponseEntity<Void> deleteCampaignActivity(@PathVariable Long campaignActivityId) {
        campaignActivityService.deleteCampaignActivity(campaignActivityId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieve all campaign activities.
     *
     * @return a list of campaign activity responses
     */
    @GetMapping("/activities")
    public ResponseEntity<List<CampaignActivityResponse>> getCampaignActivities() {
        return ResponseEntity.ok(campaignActivityService.getAllCampaignActivities());
    }

    /**
     * Retrieves the total number of campaign activities.
     *
     * @return the total count of campaign activities
     */
    @GetMapping("/activities/count")
    public ResponseEntity<Long> getTotalCampaignActivityCount() {
        return ResponseEntity.ok(campaignActivityService.getTotalCampaignActivityCount());
    }

    /**
     * Retrieves a campaign activity by its ID.
     *
     * @param campaignActivityId the ID of the campaign activity to retrieve
     * @return the campaign activity response for the specified ID
     */
    @GetMapping("/activities/{campaignActivityId}")
    public ResponseEntity<CampaignActivityResponse> getCampaignActivity(@PathVariable Long campaignActivityId) {
        log.info("Fetching campaign activity with ID: {}", campaignActivityId);
        return ResponseEntity.ok(campaignActivityService.getCampaignActivity(campaignActivityId));
    }
}