package com.axon.core_service.domain.campaign;

import com.axon.core_service.domain.dto.event.EventStatus;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "campaigns")
public class Campaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Long targetSegmentId;

    private String rewardType;

    @Column(length = 2000)
    private String rewardPayload;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Event> events = new ArrayList<>();

    public Campaign(String name) {
        this.name = name;
    }

    public void updateSchedule(LocalDateTime startAt, LocalDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void updateReward(String rewardType, String rewardPayload) {
        this.rewardType = rewardType;
        this.rewardPayload = rewardPayload;
    }

    public void updateBasicInfo(String name, Long targetSegmentId) {
        this.name = name;
        this.targetSegmentId = targetSegmentId;
    }
}
