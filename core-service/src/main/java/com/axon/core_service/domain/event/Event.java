package com.axon.core_service.domain.event;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.core_service.domain.common.BaseTimeEntity;
import com.axon.core_service.domain.dto.filter.FilterDetail;
import com.axon.core_service.domain.dto.filter.converter.FilterDetailConverter;
import com.axon.messaging.EventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "events")
public class Event extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "limit_count")
    private Integer limitCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EventStatus eventStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;


    @Enumerated(EnumType.STRING)
    @Column(name= "event_type", nullable = false)
    private EventType eventType;

    @Convert(converter = FilterDetailConverter.class)
    @Column(name = "filters", columnDefinition = "JSON")
    private List<FilterDetail> filters;

    @Builder
    public Event(Campaign campaign, String eventName, Integer limitCount, EventStatus eventStatus, LocalDateTime startDate, LocalDateTime endDate, EventType eventType, List<FilterDetail> filters) {
        this.campaign = campaign;
        this.eventName = eventName;
        this.limitCount = limitCount;
        this.eventStatus = eventStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.eventType = eventType;
        this.filters = filters;
    }

    public Long getCampaignId() {
        return campaign != null ? campaign.getId() : null;
    }

    public void updateInfo(String eventName, Integer limitCount) {
        this.eventName = eventName;
        this.limitCount = limitCount;
    }

    public void changeStatus(EventStatus status) {
        this.eventStatus = status;
    }

    public void changeDates(LocalDateTime startDate, LocalDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}


