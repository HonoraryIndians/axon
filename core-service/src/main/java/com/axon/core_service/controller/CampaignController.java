package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.service.CampaignService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * Creates a new campaign from the provided request.
     *
     * @param request the campaign details to create
     * @return the created CampaignResponse
     */
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    /**
     * Retrieve all campaigns.
     *
     * @return a list of CampaignResponse objects representing all campaigns
     */
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns() {
        return ResponseEntity.ok(campaignService.getCampaigns());
    }

    /**
     * Retrieves a campaign by its identifier.
     *
     * @param id the campaign's identifier
     * @return the campaign represented as a {@code CampaignResponse}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    /**
     * Updates an existing campaign identified by the given id.
     *
     * @param id      the id of the campaign to update
     * @param request the new campaign data
     * @return the updated CampaignResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@PathVariable Long id,
                                                           @RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    /**
     * Deletes the campaign identified by the given id.
     *
     * @param id the id of the campaign to delete
     * @return a ResponseEntity with HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Checks whether a campaign name is already in use.
     *
     * @param name the campaign name to check
     * @return `true` if the campaign name is taken, `false` otherwise
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkCampaignName(@RequestParam String name) {
        return ResponseEntity.ok(campaignService.isCampaignNameTaken(name));
    }
}