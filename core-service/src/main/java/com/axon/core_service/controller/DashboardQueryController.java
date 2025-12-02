package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.llm.DashboardQueryRequest;
import com.axon.core_service.domain.dto.llm.DashboardQueryResponse;
import com.axon.core_service.service.llm.LLMQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryController {

    private final LLMQueryService llmQueryService;

    @PostMapping("/campaign/{campaignId}/query")
    public ResponseEntity<DashboardQueryResponse> queryDashboard(
            @PathVariable Long campaignId,
            @RequestBody DashboardQueryRequest request) {
        log.info("DashboardQueryController queryDashboard campaignId: {}, request: {}", campaignId, request);
        DashboardQueryResponse response = llmQueryService.processQuery(campaignId, request.getQuery());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activity/{activityId}/query")
    public ResponseEntity<DashboardQueryResponse> queryActivityDashboard(
            @PathVariable Long activityId,
            @RequestBody DashboardQueryRequest request) {
        log.info("DashboardQueryController queryActivityDashboard activityId: {}, request: {}", activityId, request);
        DashboardQueryResponse response = llmQueryService.processQueryByActivity(activityId, request.getQuery());
        return ResponseEntity.ok(response);
    }
}
