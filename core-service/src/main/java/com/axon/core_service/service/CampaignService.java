package com.axon.core_service.service;


import com.axon.core_service.domain.event.Event;
import com.axon.core_service.repository.EventEntryRepository;
import com.axon.core_service.repository.EventRepository;
import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.repository.CampaignRepository;
import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;
import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.domain.dto.event.EventRequest;
import com.axon.core_service.domain.dto.event.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;

    // 새 캠페인을 생성하면서 한도·일정·보상 정책을 함께 세팅한다.
    public CampaignResponse createCampaign(CampaignRequest request) {
        Campaign campaign = new Campaign(request.getName());
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    // 캠페인의 기본 정보와 정책을 수정하고 상태 전환을 검증한다.
    public CampaignResponse updateCampaign(Long id, CampaignRequest request) {
        Campaign campaign = findCampaign(id);
        applyCampaignPolicies(campaign, request);
        return CampaignResponse.from(campaign);
    }


    // 모든 캠페인을 한 번에 조회한다.
    public List<CampaignResponse> getCampaigns() {
        return campaignRepository.findAll().stream()
                .map(CampaignResponse::from)
                .toList();
    }

    // 단일 캠페인 상세 정보를 조회한다.
    public CampaignResponse getCampaign(Long id) {
        return CampaignResponse.from(findCampaign(id));
    }

    // 캠페인과 연관 이벤트를 제거한다.
    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }

    // 캠페인 하위 이벤트를 생성하며 선착순 한도와 상태를 지정한다.
    public EventResponse createEvent(Long campaignId, EventRequest request) {
        Campaign campaign = findCampaign(campaignId);
        Event event = Event.builder()
                .campaign(campaign)
                .eventName(request.getName())
                .limitCount(request.getLimitCount())
                .eventStatus(request.getStatus())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .eventType(request.getEventType())
                .build();
        Event saved = eventRepository.save(event);
        return EventResponse.from(saved);
    }

    // 이벤트의 기본 설정(이름·한도)을 갱신하고 상태 전환을 검증한다.
    public EventResponse updateEvent(Long eventId, EventRequest request) {
        Event event = findEvent(eventId);
        event.updateInfo(request.getName(), request.getLimitCount());
        validateStatusTransition(event.getEventStatus(), request.getStatus());
        event.changeStatus(request.getStatus());
        event.changeDates(request.getStartDate(), request.getEndDate());
        return EventResponse.from(event);
    }

    // 이벤트의 상태만 변경한다.
    public EventResponse changeEventStatus(Long eventId, EventStatus status) {
        Event event = findEvent(eventId);
        validateStatusTransition(event.getEventStatus(), status);
        event.changeStatus(status);
        return EventResponse.from(event);
    }

    // 캠페인에 속한 이벤트 목록을 조회한다.
    public List<EventResponse> getEvents(Long campaignId) {
        return eventRepository.findAllByCampaign_Id(campaignId).stream()
                .map(EventResponse::from)
                .toList();
    }

    // 이벤트를 삭제한다.
    public void deleteEvent(Long eventId) {
        eventRepository.deleteById(eventId);
    }

    // 모든 이벤트를 한 번에 조회한다.
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(event -> {
                    long participantCount = eventEntryRepository.countByEvent_Id(event.getId());
                    return EventResponse.builder()
                            .id(event.getId())
                            .campaignId(event.getCampaignId())
                            .name(event.getEventName())
                            .limitCount(event.getLimitCount())
                            .status(event.getEventStatus())
                            .start_date(event.getStartDate())
                            .end_date(event.getEndDate())
                            .eventType(event.getEventType())
                            .created_at(event.getCreatedAt())
                            .participantCount(participantCount)
                            .build();
                })
                .toList();
    }

    // 전체 이벤트의 개수를 조회한다.
    public long getTotalEventCount() {
        return eventRepository.count();
    }

    // 단일 이벤트를 조회한다.
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        long participantCount = eventEntryRepository.countByEvent_Id(eventId);
        return EventResponse.builder()
                .id(event.getId())
                .campaignId(event.getCampaignId())
                .name(event.getEventName())
                .limitCount(event.getLimitCount())
                .status(event.getEventStatus())
                .start_date(event.getStartDate())
                .end_date(event.getEndDate())
                .eventType(event.getEventType())
                .created_at(event.getCreatedAt())
                .participantCount(participantCount)
                .build();
    }

    // 캠페인 이름이 이미 사용 중인지 확인한다.
    public boolean isCampaignNameTaken(String name) {
        return campaignRepository.findByName(name).isPresent();
    }

    // 캠페인의 핵심 정책(기본 정보·일정·보상)을 일괄 적용한다。
    private void applyCampaignPolicies(Campaign campaign, CampaignRequest request) {
        campaign.updateBasicInfo(request.getName(), request.getTargetSegmentId());
        campaign.updateSchedule(request.getStartAt(), request.getEndAt());
        campaign.updateReward(request.getRewardType(), request.getRewardPayload());
    }

    // 존재하는 캠페인인지 확인하고 엔티티를 반환한다.
    private Campaign findCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("campaign not found: " + id));
    }

    // 이벤트 존재 여부를 확인하고 엔티티를 반환한다.
    private Event findEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + id));
    }

    // 허용된 상태 전환 흐름(DRAFT→ACTIVE→PAUSED/ENDED 등)인지 검증한다.
    private void validateStatusTransition(EventStatus current, EventStatus next) {
        if(current == next) return;
        if (current == EventStatus.DRAFT && next == EventStatus.ACTIVE) return;
        if (current == EventStatus.ACTIVE && (next == EventStatus.PAUSED || next == EventStatus.ENDED)) return;
        if (current == EventStatus.PAUSED && next == EventStatus.ACTIVE) return;
        throw new IllegalStateException("invalid status transition: " + current + " -> " + next);
    }
}
