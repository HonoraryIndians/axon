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
        return "index";
    }

    @GetMapping("/")
    public String welcomepage() {
        log.info("Serving welcomepage.html");
        return "welcomepage";
        //TO-DO: 로그아웃 이후 임시 대피처 재정의 필요
    }

    @GetMapping("/entry")
    public String entry() {
        log.info("Serving entry.html");
        return "entry";
    }

    @GetMapping("/admin")
    public String admin() {
        log.info("Serving admin.html");
        return "admin";
    }

    @GetMapping("/admin_board")
    public String admin_board() {
        log.info("Serving admin_board.html");
        return "admin_board";
    }

    @GetMapping("/admin_create_campaignActivitys")
    public String admin_create_event() {
        log.info("Serving admin_create_campaignActivitys.html");
        return "admin_create_campaignActivitys";
    }

    @GetMapping("/shoppingmall")
    public String shoppingmall() {
        log.info("Serving shoppingmall.html");
        return "shoppingmall";
    }
}
