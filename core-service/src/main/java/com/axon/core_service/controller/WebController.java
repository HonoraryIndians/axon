package com.axon.core_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        log.info("Serving index.html");
        return "index.html";
    }

    @GetMapping("/login-success")
    public String loginSuccess() {
        log.info("Serving login-success.html");
        return "login-success.html";
    }
}
