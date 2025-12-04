package com.axon.core_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    private final com.axon.core_service.repository.CampaignActivityRepository campaignActivityRepository;
    private final com.axon.core_service.repository.CampaignRepository campaignRepository;

    @GetMapping("/admin/dashboard/{activityId}")
    public String dashboardView(@PathVariable Long activityId, Model model) {
        // Fetch activity to get parent campaign info
        var activity = campaignActivityRepository.findById(activityId).orElse(null);
        
        model.addAttribute("campaignId", activityId); // Keeping as campaignId for existing JS compatibility (this is actually activityId)
        model.addAttribute("activityId", activityId); // Explicitly adding activityId
        
        if (activity != null) {
            model.addAttribute("campaignName", activity.getName()); // Current Activity Name
            
            // Parent Campaign Info
            if (activity.getCampaign() != null) {
                model.addAttribute("parentCampaignId", activity.getCampaign().getId());
                model.addAttribute("parentCampaignName", activity.getCampaign().getName());
            }
        } else {
            model.addAttribute("campaignName", "Activity #" + activityId);
        }
        
        return "dashboard";
    }

    @GetMapping("/admin/dashboard/cohort/{activityId}")
    public String cohortDashboardView(@PathVariable Long activityId, Model model) {
        model.addAttribute("activityId", activityId);
        var activity = campaignActivityRepository.findById(activityId).orElse(null);
        if (activity != null) {
            model.addAttribute("activityName", activity.getName());
            model.addAttribute("campaignId", activity.getCampaign().getId()); // Add campaignId for chatbot context
        } else {
            model.addAttribute("activityName", "Activity #" + activityId);
        }
        return "cohort-dashboard";
    }

    @GetMapping("/admin/dashboard/campaign/{campaignId}")
    public String campaignDashboardView(@PathVariable Long campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        var campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign != null) {
            model.addAttribute("campaignName", campaign.getName());
        } else {
            model.addAttribute("campaignName", "Campaign #" + campaignId);
        }
        return "campaign-dashboard";
    }

    @GetMapping("/admin/dashboard/overview")
    public String globalDashboardView() {
        return "global-dashboard";
    }
}
