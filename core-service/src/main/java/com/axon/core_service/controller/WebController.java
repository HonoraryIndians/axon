package com.axon.core_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class WebController {

    /**
     * Serve the application's index page.
     *
     * @return the view name "index" used to render the application's index page
     */
    @GetMapping("/old")
    public String oldIndex() {
        log.info("Serving index.html");
        return "index";
    }

    /**
     * Render the application's welcome page.
     *
     * @return the view name "welcomepage"
     */
    @GetMapping("/")
    public String index() {
        log.info("Redirecting to /mainshop");
        return "redirect:/mainshop";
    }

    /**
     * Serves the entry page view.
     *
     * @return the logical view name "entry" to render the entry page
     */
    @GetMapping("/entry")
    public String entry() {
        log.info("Serving entry.html");
        return "entry";
    }

    /**
     * Serve the admin portal home.
     *
     * @return the view name "admin_home"
     */
    @GetMapping("/admin")
    public String adminHome() {
        log.info("Serving admin_home.html");
        return "admin_home";
    }

    /**
     * Serve the campaign management board.
     *
     * @return the view name "admin"
     */
    @GetMapping("/admin/campaigns")
    public String adminCampaigns() {
        log.info("Serving admin.html (Campaigns)");
        return "admin";
    }

    /**
     * Serve the coupon management board.
     *
     * @return the view name "admin_coupons"
     */
    @GetMapping("/admin/coupons")
    public String adminCoupons() {
        log.info("Serving admin_coupons.html");
        return "admin_coupons";
    }

    /**
     * Serve the system monitoring page.
     *
     * @return the view name "admin_monitoring"
     */
    @GetMapping("/admin/monitoring")
    public String adminMonitoring() {
        log.info("Serving admin_monitoring.html");
        return "admin_monitoring";
    }

    /**
     * Serves the admin page for creating campaign activities.
     *
     * @return the view name "admin_create_campaignActivitys"
     */
    @GetMapping("/admin_create_campaignActivitys")
    public String admin_create_event() {
        log.info("Serving admin_create_campaignActivitys.html");
        return "admin_create_campaignActivitys";
    }

    /**
     * Serves the shopping mall page.
     *
     * @return the view name "shoppingmall"
     */
    @GetMapping("/shoppingmall")
    public String shoppingmall() {
        log.info("Serving shoppingmall.html");
        return "shoppingmall";
    }

    @GetMapping("/admin/events")
    public String eventBoard() {
        log.info("Serving event-board.html");
        return "event-board";
    }

    /**
     * Serves the real-time dashboard page for a specific campaign activity.
     *
     * @return the view name "dashboard"
     */
    @GetMapping("/dashboard/activity/{activityId}")
    public String activityDashboard() {
        log.info("Serving dashboard.html for activity-level monitoring");
        return "dashboard";
    }
}