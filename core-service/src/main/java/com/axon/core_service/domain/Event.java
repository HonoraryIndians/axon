package com.axon.core_service.domain;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.campaign.CampaignStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    private String name; // 이벤트 이름

    private Integer limitCount; // 선착순 제한 인원

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CampaignStatus campaignStatus;

}
