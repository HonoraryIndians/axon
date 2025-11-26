package com.axon.core_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    @GetMapping("/admin/dashboard/{activityId}")
    public String dashboardView(@PathVariable Long activityId, Model model) {
        model.addAttribute("campaignId", activityId); // Keeping attribute name as campaignId for compatibility with
                                                      // existing JS/HTML
        model.addAttribute("campaignName", "Activity #" + activityId);
        return "dashboard";
    }

    @GetMapping("/admin/dashboard/cohort/{activityId}")
    public String cohortDashboardView(@PathVariable Long activityId, Model model) {
        model.addAttribute("activityId", activityId);
        model.addAttribute("activityName", "Activity #" + activityId);
        return "cohort-dashboard";
    }
}
