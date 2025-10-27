package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.domain.dto.event.EventRequest;
import com.axon.core_service.domain.dto.event.EventResponse;
import com.axon.core_service.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService campaignService;

    // JSON형식의 요청을 받으면 아래 함수를 통해 값을 dto로 변환하여 처리합니다.

    // 새 캠페인을 등록하며 한도·일정·보상 정책을 전달한다.
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    // 전체 캠페인을 조회한다.
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns() {

        return ResponseEntity.ok(campaignService.getCampaigns());
    }

    // 단일 캠페인의 상세 정보를 확인한다.
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    // 캠페인의 기본 정보·정책을 수정한다.
    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(@PathVariable Long id, @RequestBody @Valid CampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    // 캠페인을 삭제한다.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    // 캠페인 하위 이벤트를 등록하고 한도·상태를 설정한다.
    @PostMapping("/{campaignId}/events")
    public ResponseEntity<EventResponse> createEvent(
            @PathVariable Long campaignId,
            @RequestBody @Valid EventRequest request
    ) {
        return ResponseEntity.ok(campaignService.createEvent(campaignId, request));
    }


    // 특정 캠페인에 속한 이벤트 목록을 조회한다.
    @GetMapping("/{campaignId}/events")
    public ResponseEntity<List<EventResponse>> getEvents(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignService.getEvents(campaignId));
    }

    // 이벤트의 기본 정보를 수정한다.
    @PutMapping("/events/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long eventId,
            @RequestBody @Valid EventRequest request
    ) {
        return ResponseEntity.ok(campaignService.updateEvent(eventId, request));
    }

    // 이벤트 상태만 변경한다.
    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<EventResponse> changeEventStatus(
            @PathVariable Long eventId,
            @RequestParam EventStatus status
    ) {
        return ResponseEntity.ok(campaignService.changeEventStatus(eventId, status));
    }

    // 이벤트를 삭제한다.
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        campaignService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    // 전체 이벤트를 조회한다.
    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getEvents() {
        return ResponseEntity.ok(campaignService.getAllEvents());
    }

    // 전체 이벤트의 개수를 조회한다.
    @GetMapping("/events/count")
    public ResponseEntity<Long> getTotalEventCount() {
        return ResponseEntity.ok(campaignService.getTotalEventCount());
    }

    // 단일 이벤트의 상세 정보를 조회한다.
    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(campaignService.getEvent(eventId));
    }

    // 캠페인 이름 중복을 확인한다.
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkCampaignName(@RequestParam String name) {
        return ResponseEntity.ok(campaignService.isCampaignNameTaken(name));
    }
}
