package com.axon.core_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class WebController {

    @GetMapping("/index")
    public String index() {
        log.info("Serving index.html");
        return "index.html";
    }

    @GetMapping("/")
    public String loginSuccess() {
        log.info("Serving welcomepage.html");
        return "welcomepage.html";
        //TO-DO: 로그아웃 이후 임시 대피처 재정의 필요
    }

    @GetMapping("/entry")
    public String entry() {
        log.info("Serving entry.html");
        return "entry.html";
    }

    @GetMapping("/admin")
    public String admin() {
        log.info("Serving admin.html");
        return "admin.html";
    }
}
