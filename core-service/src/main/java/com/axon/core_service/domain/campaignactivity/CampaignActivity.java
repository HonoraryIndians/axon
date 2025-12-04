package com.axon.core_service.domain.campaignactivity;

import com.axon.core_service.domain.campaign.Campaign;
import com.axon.core_service.domain.common.BaseTimeEntity;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.domain.dto.campaignactivity.filter.converter.FilterDetailConverter;
import com.axon.core_service.domain.product.Product;
import com.axon.messaging.CampaignActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

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

    @Convert(converter = FilterDetailConverter.class)
    @Column(name = "filters", columnDefinition = "JSON")
    private List<FilterDetail> filters;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "budget", precision = 12, scale = 2)
    private BigDecimal budget;  // Activity-specific marketing budget for ROAS calculation

    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Constructs a CampaignActivity with the specified association and attributes.
     *
     * @param campaign   the owning Campaign (may be null until associated)
     * @param name       the activity's display name
     * @param limitCount maximum allowed count for the activity, or {@code null} for no limit
     * @param status     initial lifecycle status of the activity
     * @param startDate  activity start date and time (inclusive)
     * @param endDate    activity end date and time (inclusive)
     * @param activityType type categorizing the activity
     * @param filters    list of filter rules applied to the activity (may be null or empty)
     * @param price      product price for this activity
     * @param quantity   available quantity
     * @param budget     marketing budget allocated for this activity
     * @param filters      list of filter rules applied to the activity (may be null
     *                     or empty)
     */
    @Builder
    public CampaignActivity(Campaign campaign,
                            Product product,
                            String name,
                            Integer limitCount,
                            CampaignActivityStatus status,
                            LocalDateTime startDate,
                            LocalDateTime endDate,
                            CampaignActivityType activityType,
                            List<FilterDetail> filters,
                            BigDecimal price,
                            Integer quantity,
                            BigDecimal budget,
                            String imageUrl
                            ) {
        this.campaign = campaign;
        this.product = product;
        this.name = name;
        this.limitCount = limitCount;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.activityType = activityType;
        this.filters = filters;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.budget = budget;
    }

    /**
     * Gets the id of the associated campaign.
     *
     * @return the campaign's id, or {@code null} if no campaign is associated
     */
    public Long getCampaignId() {
        return campaign != null ? campaign.getId() : null;
    }

    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    /**
     * Updates the activity's display name and participant limit.
     *
     * @param name       the new name for the activity
     * @param limitCount the maximum allowed count for the activity; may be null to
     *                   indicate no limit
     */
    public void updateInfo(String name, Integer limitCount) {
        this.name = name;
        this.limitCount = limitCount;
    }

    /**
     * Sets the activity's status to the specified value.
     *
     * @param nextStatus the new status to assign to this activity
     */
    public void changeStatus(CampaignActivityStatus nextStatus) {
        this.status = nextStatus;
    }

    /**
     * Updates the activity's start and end date range.
     *
     * @param startDate the new start date and time for the activity
     * @param endDate   the new end date and time for the activity
     */
    public void changeDates(LocalDateTime startDate, LocalDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Replaces the activity's filters with the provided list.
     *
     * @param filters the new list of FilterDetail objects to assign to this
     *                activity; may be null to remove all filters
     */
    public void setFilters(List<FilterDetail> filters) {
        this.filters = filters;
    }

    public void updateProductInfo(Product product, BigDecimal price, Integer quantity) {
        this.product = product;
        this.price = price;
        this.quantity = quantity;
    }

    public void updateActivityType(CampaignActivityType activityType) {
        this.activityType = activityType;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void updateBudget(BigDecimal budget) {
        this.budget = budget;
    }

    /**
     * Associates this activity with the given Campaign.
     *
     * @param campaign the Campaign to associate with this activity; may be
     *                 {@code null} to remove the association
     */
    public void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}