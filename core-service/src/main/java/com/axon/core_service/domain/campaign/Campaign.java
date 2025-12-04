package com.axon.core_service.domain.campaign;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private BigDecimal budget; // 캠페인 예산

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CampaignActivity> campaignActivities = new ArrayList<>();

    /**
     * Creates a Campaign with the specified name.
     *
     * @param name the campaign's name
     */
    public Campaign(String name) {
        this.name = name;
    }

    /**
     * Creates a Campaign populated with the provided identifying, scheduling,
     * targeting, reward values, and budget.
     *
     * @param name            the campaign name
     * @param startAt         the campaign start time, or null if unspecified
     * @param endAt           the campaign end time, or null if unspecified
     * @param targetSegmentId the target segment identifier, or null if the campaign
     *                        has no segment target
     * @param rewardType      the reward type identifier or descriptor, or null if
     *                        none
     * @param rewardPayload   the reward payload or parameters (up to 2000
     *                        characters), or null if none
     * @param budget          the campaign budget, or null if unspecified
     */
    @Builder
    public Campaign(String name,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long targetSegmentId,
            String rewardType,
            String rewardPayload,
            java.math.BigDecimal budget) {
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        this.targetSegmentId = targetSegmentId;
        this.rewardType = rewardType;
        this.rewardPayload = rewardPayload;
        this.budget = budget;
    }

    /**
     * Update the campaign's start and end times.
     *
     * @param startAt the new start time; may be {@code null} to clear the start
     *                time
     * @param endAt   the new end time; may be {@code null} to clear the end time
     */
    public void updateSchedule(LocalDateTime startAt, LocalDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /**
     * Set the campaign's reward type and associated payload.
     *
     * @param rewardType    the reward type identifier or name
     * @param rewardPayload the reward details or serialized payload for the reward
     */
    public void updateReward(String rewardType, String rewardPayload) {
        this.rewardType = rewardType;
        this.rewardPayload = rewardPayload;
    }

    /**
     * Updates the campaign's display name and associated target segment identifier.
     *
     * @param name            the new campaign name
     * @param targetSegmentId the identifier of the target segment to associate with
     *                        the campaign, or `null` to remove it
     */
    public void updateBasicInfo(String name, Long targetSegmentId) {
        this.name = name;
        this.targetSegmentId = targetSegmentId;
    }

    public void updateBudget(BigDecimal budget) {
        this.budget = budget;
    }
}