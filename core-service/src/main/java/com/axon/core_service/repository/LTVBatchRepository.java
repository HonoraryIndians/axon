package com.axon.core_service.repository;


import com.axon.core_service.domain.dashboard.LTVBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LTVBatchRepository extends JpaRepository<LTVBatch, Long> {
    /**
     * 특정 캠페인 활동의 모든 월별 통계 조회 (그래프용)
     */
    List<LTVBatch> findByCampaignActivityIdOrderByMonthOffsetAsc(Long campaignActivityId);

    /**
     * 특정 캠페인 활동의 특정 월 통계 조회
     */
    Optional<LTVBatch> findByCampaignActivityIdAndMonthOffset(Long campaignActivityId, Integer monthOffset);

    /**
     * 특정 캠페인 활동의 통계 존재 여부
     */
    boolean existsByCampaignActivityId(Long campaignActivityId);

    /**
     * 배치 작업 대상 캠페인 조회 (12개월 이내 시작)
     */
    @Query("SELECT DISTINCT c.id FROM CampaignActivity c " +
            "WHERE c.startDate >= :twelveMonthsAgo " +
            "AND c.startDate <= :now")
    List<Long> findActivitiesForBatchProcessing(
            @Param("twelveMonthsAgo") java.time.LocalDateTime twelveMonthsAgo,
            @Param("now") java.time.LocalDateTime now
    );
}
