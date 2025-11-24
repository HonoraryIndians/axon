package com.axon.core_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Slf4j
@Controller
public class WebController {

    /**
     * Serve the application's index page.
     *
     * @return the view name "index" used to render the application's index page
     */
    @GetMapping("/index")
    public String index() {
        log.info("Serving index.html");
        return "index";
    }

    /**
     * Render the application's welcome page.
     *
     * @return the view name "welcomepage"
     */
    @GetMapping("/")
    public String welcomepage() {
        log.info("Serving welcomepage.html");
        return "welcomepage";
        // TO-DO: 로그아웃 이후 임시 대피처 재정의 필요
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
     * Serve the admin view.
     *
     * @return the view name "admin"
     */
    @GetMapping("/admin")
    public String admin() {
        log.info("Serving admin.html");
        return "admin";
    }

    /**
     * Display the admin dashboard page.
     *
     * @return the view name "admin_board"
     */
    @GetMapping("/admin_board")
    public String admin_board() {
        log.info("Serving admin_board.html");
        return "admin_board";
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
        return "forward:/admin/events/event-board.html";
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