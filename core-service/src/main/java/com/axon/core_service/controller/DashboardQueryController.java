package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.llm.DashboardQueryRequest;
import com.axon.core_service.domain.dto.llm.DashboardQueryResponse;
import com.axon.core_service.service.llm.LLMQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardQueryController {

    private final LLMQueryService llmQueryService;

    @PostMapping("/campaign/{campaignId}/query")
    public ResponseEntity<DashboardQueryResponse> queryDashboard(
            @PathVariable Long campaignId,
            @RequestBody DashboardQueryRequest request) {

        DashboardQueryResponse response = llmQueryService.processQuery(campaignId, request.getQuery());
        return ResponseEntity.ok(response);
    }
}
