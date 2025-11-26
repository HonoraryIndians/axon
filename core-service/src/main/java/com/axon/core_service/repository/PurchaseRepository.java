package com.axon.core_service.repository;


import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.domain.purchase.PurchaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 사용자별 구매 내역 조회
    List<Purchase> findByUserId(Long userId);

    // 상품별 구매 내역 조회
    List<Purchase> findByProductId(Long productId);

    // 구매 타입별 조회
    List<Purchase> findByPurchaseType(PurchaseType purchaseType);

    // 캠페인별 구매 내역 조회
    List<Purchase> findByCampaignActivityId(Long campaignActivityId);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cohort Analysis Queries
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 특정 Activity의 특정 기간 내 모든 구매 조회
     */
    @Query("SELECT p FROM Purchase p " +
           "WHERE p.campaignActivityId = :activityId " +
           "AND p.purchaseAt >= :startDate " +
           "AND p.purchaseAt < :endDate " +
           "ORDER BY p.purchaseAt ASC")
    List<Purchase> findByCampaignActivityIdAndPeriod(
            @Param("activityId") Long activityId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * 특정 Activity의 첫 구매 고객만 조회 (Cohort 정의)
     * userId별 최초 구매만 반환
     */
    @Query(value = "SELECT p.* FROM purchases p " +
                   "INNER JOIN ( " +
                   "    SELECT user_id, MIN(purchase_at) as first_purchase " +
                   "    FROM purchases " +
                   "    WHERE campaign_activity_id = :activityId " +
                   "    AND purchase_at >= :startDate " +
                   "    AND purchase_at < :endDate " +
                   "    GROUP BY user_id " +
                   ") first ON p.user_id = first.user_id AND p.purchase_at = first.first_purchase " +
                   "WHERE p.campaign_activity_id = :activityId",
           nativeQuery = true)
    List<Purchase> findFirstPurchasesByActivityAndPeriod(
            @Param("activityId") Long activityId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * 특정 userId 목록의 모든 구매 이력 조회 (재구매 추적용)
     */
    @Query("SELECT p FROM Purchase p " +
           "WHERE p.userId IN :userIds " +
           "ORDER BY p.userId, p.purchaseAt ASC")
    List<Purchase> findByUserIdIn(@Param("userIds") List<Long> userIds);

    /**
     * 특정 Activity에서 재구매한 고객 수 조회
     */
    @Query("SELECT COUNT(DISTINCT p.userId) FROM Purchase p " +
           "WHERE p.campaignActivityId = :activityId " +
           "AND p.userId IN :cohortUserIds " +
           "GROUP BY p.userId " +
           "HAVING COUNT(p.id) > 1")
    Long countRepeatCustomers(
            @Param("activityId") Long activityId,
            @Param("cohortUserIds") List<Long> cohortUserIds
    );
}
