package com.axon.core_service.domain.campaignactivityentry;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.common.BaseTimeEntity;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "campaign_activity_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignActivityEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_activity_id")
    private CampaignActivity campaignActivity;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CampaignActivityEntryStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "additional_data")
    private String info;

    /**
     * Initializes a CampaignActivityEntry with the provided campaign activity, user, product, and request timestamp.
     *
     * The entry's status is set to `PENDING`.
     *
     * @param campaignActivity the associated campaign activity
     * @param userId the identifier of the user who requested the entry
     * @param productId the identifier of the product related to the entry (may be null)
     * @param requestedAt the instant when the entry was requested
     */
    private CampaignActivityEntry(CampaignActivity campaignActivity,
                                  Long userId,
                                  Long productId,
                                  Instant requestedAt) {
        this.campaignActivity = campaignActivity;
        this.userId = userId;
        this.productId = productId;
        this.requestedAt = LocalDateTime.ofInstant(requestedAt, ZoneId.of("Asia/Seoul"));
        this.status = CampaignActivityEntryStatus.PENDING;
    }

    /**
     * Creates a new CampaignActivityEntry for the given campaign activity with the provided user, product, and request time.
     *
     * @param campaignActivity the associated CampaignActivity
     * @param userId the ID of the user who requested the entry
     * @param productId the ID of the related product; may be null
     * @param requestedAt the timestamp when the entry was requested
     * @return the newly created CampaignActivityEntry with initial status set to PENDING
     */
    public static CampaignActivityEntry create(CampaignActivity campaignActivity,
                                               Long userId,
                                               Long productId,
                                               Instant requestedAt) {
        return new CampaignActivityEntry(campaignActivity, userId, productId, requestedAt);
    }

    /**
     * Sets the entry's status to the provided value.
     *
     * @param status the new status for this campaign activity entry
     */
    public void updateStatus(CampaignActivityEntryStatus status) {
        this.status = status;
    }

    /**
     * Set the timestamp when this entry was processed.
     *
     * @param processedAt the processing timestamp to record
     */
    public void markProcessedAt(Instant processedAt) {
        this.processedAt = LocalDateTime.ofInstant(processedAt, ZoneId.of("Asia/Seoul"));
    }

    /**
     * Sets the processedAt timestamp to the current instant.
     */
    public void markProcessedNow() {
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Update the entry's product identifier when a non-null value is provided.
     *
     * @param productId the new product identifier; if `null`, the current productId is not modified
     */
    public void updateProduct(Long productId) {
        if (productId != null) {
            this.productId = productId;
        }
    }

    /**
     * Sets the entry's additional information.
     *
     * @param info the additional information to store; may be `null` to clear existing info
     */
    public void updateInfo(String info) {
        this.info = info;
    }
}