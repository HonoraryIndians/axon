package com.axon.core_service.controller;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.domain.dto.campaignactivityentry.CampaignActivityEntryPageResponse;
import com.axon.core_service.service.CampaignActivityEntryQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaign-activities")
@RequiredArgsConstructor
@Slf4j
public class CampaignActivityEntryQueryController {

    private final CampaignActivityEntryQueryService campaignActivityEntryQueryService;

    @GetMapping("/{campaignActivityId}/entries")
    public ResponseEntity<CampaignActivityEntryPageResponse> getEntries(@PathVariable Long campaignActivityId,
                                                                        @RequestParam(required = false) CampaignActivityEntryStatus status,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        log.info("캠페인 활동 참가자 조회 요청: campaignActivityId={}, status={}, page={}, size={}",
                campaignActivityId, status, page, size);

        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        CampaignActivityEntryPageResponse response = campaignActivityEntryQueryService.findEntries(campaignActivityId, status, pageable);
        return ResponseEntity.ok(response);
    }
}
