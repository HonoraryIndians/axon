package com.axon.core_service.domain.campaignactivity;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.common.BaseTimeEntity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.messaging.CampaignActivityType;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "campaign_activities")
public class CampaignActivity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "limit_count")
    private Integer limitCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CampaignActivityStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private CampaignActivityType activityType;

    @Builder
    public CampaignActivity(Campaign campaign,
                            String name,
                            Integer limitCount,
                            CampaignActivityStatus status,
                            LocalDateTime startDate,
                            LocalDateTime endDate,
                            CampaignActivityType activityType) {
        this.campaign = campaign;
        this.name = name;
        this.limitCount = limitCount;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.activityType = activityType;
    }

    public Long getCampaignId() {
        return campaign != null ? campaign.getId() : null;
    }

    public void updateInfo(String name, Integer limitCount) {
        this.name = name;
        this.limitCount = limitCount;
    }

    public void changeStatus(CampaignActivityStatus nextStatus) {
        this.status = nextStatus;
    }

    public void changeDates(LocalDateTime startDate, LocalDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}
