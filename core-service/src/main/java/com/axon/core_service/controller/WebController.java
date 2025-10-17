package com.axon.core_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class WebController {

    /**
     * Serves the application's index HTML view.
     *
     * @return the view name "index.html" to render the index page
     */
    @GetMapping("/index")
    public String index() {
        log.info("Serving index.html");
        return "index.html";
    }

    /**
     * Handles GET requests to the application root and resolves the "login-success.html" view.
     *
     * @return the view name "login-success.html"
     */
    @GetMapping("/")
    public String loginSuccess() {
        log.info("Serving login-success.html");
        return "login-success.html";
        //TO-DO: 로그아웃 이후 임시 대피처 재정의 필요
    }


    /**
     * Serves the entry page.
     *
     * @return the view name "entry.html"
     */
    @GetMapping("/entry")
    public String entry() {
        log.info("Serving entry.html");
        return "entry.html";
    }
}