package com.axon.core_service.domain;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaign.CampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "limit_count")
    private Integer limitCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CampaignStatus campaignStatus;

    @Builder
    public Event(Campaign campaign, String eventName, Integer limitCount, CampaignStatus campaignStatus) {
        this.campaign = campaign;
        this.eventName = eventName;
        this.limitCount = limitCount;
        this.campaignStatus = campaignStatus;
    }

    public Long getCampaignId() {
        return campaign != null ? campaign.getId() : null;
    }

    public void updateInfo(String eventName, Integer limitCount) {
        this.eventName = eventName;
        this.limitCount = limitCount;
    }

    public void changeStatus(CampaignStatus status) {
        this.campaignStatus = status;
    }

    void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}
