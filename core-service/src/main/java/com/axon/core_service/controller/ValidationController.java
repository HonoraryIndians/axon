package com.axon.core_service.controller;

import com.axon.messaging.dto.validation.ValidationResponse;
import com.axon.core_service.service.validation.CampaignActivityLimit.DynamicValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/validation")
public class ValidationController {
    private final DynamicValidationService dynamicValidationService;

    @GetMapping
    public ResponseEntity<ValidationResponse> validateCampaignActivityLimit(@AuthenticationPrincipal UserDetails userdetails, @RequestParam Long campaignActivityId) {
        long userId = Long.parseLong(userdetails.getUsername());
        if(campaignActivityId == null) {return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();}
        ValidationResponse res = dynamicValidationService.validate(userId, campaignActivityId);
        return ResponseEntity.ok(res);
    }
}
