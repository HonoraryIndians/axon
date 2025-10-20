package com.axon.core_service.domain;

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
    private int id;


    private int campaignId; // 캠페인 아이디
    private String eventName; // 이벤트 이름

    private int limitCount; // 선착순 제한 인원

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CampaignStatus campaignStatus;

}
