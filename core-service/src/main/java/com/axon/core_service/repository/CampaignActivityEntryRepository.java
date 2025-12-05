package com.axon.core_service.repository;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntry;
import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignActivityEntryRepository extends JpaRepository<CampaignActivityEntry, Long> {

    /**
     * Finds the campaign activity entry for the given campaign activity and user.
     *
     * @param campaignActivityId the ID of the campaign activity
     * @param userId             the ID of the user
     * @return an Optional containing the matching CampaignActivityEntry if present, otherwise empty
     */
    Optional<CampaignActivityEntry> findByCampaignActivity_IdAndUserId(Long campaignActivityId, Long userId);

    /**
     * Retrieves a paginated list of CampaignActivityEntry objects for the specified campaign activity.
     *
     * @param campaignActivityId the ID of the campaign activity whose entries to retrieve
     * @param pageable           pagination and sorting information to apply to the result
     * @return a page of CampaignActivityEntry matching the specified campaign activity ID
     */
    Page<CampaignActivityEntry> findByCampaignActivity_Id(Long campaignActivityId, Pageable pageable);

    /**
     * Retrieves a page of CampaignActivityEntry records for the specified campaign activity and status.
     *
     * @param campaignActivityId the ID of the campaign activity to filter by
     * @param status             the status to filter entries by
     * @param pageable           paging and sorting information
     * @return a page of CampaignActivityEntry objects matching the given campaign activity ID and status
     */
    Page<CampaignActivityEntry> findByCampaignActivity_IdAndStatus(Long campaignActivityId,
                                                                   CampaignActivityEntryStatus status,
                                                                   Pageable pageable);

    /**
     * Count CampaignActivityEntry records associated with a campaign activity.
     *
     * @param campaignActivityId the ID of the campaign activity to count entries for
     * @return the number of CampaignActivityEntry records linked to the specified campaign activity ID
     */
    Long countByCampaignActivity_Id(Long campaignActivityId);
    Long countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
            Long campaignActivityId,
            CampaignActivityEntryStatus status,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime
    );

    /**
     * Bulk 조회: 여러 (activityId, userId) 조합의 Entry 한번에 조회
     *
     * 역할: 배치 처리 시 기존 Entry 존재 여부 확인용
     *
     * @param activityIds 캠페인 활동 ID 리스트
     * @param userIds 유저 ID 리스트
     * @return 조건에 맞는 Entry 리스트
     */
    @Query("SELECT e FROM CampaignActivityEntry e " +
            "WHERE e.campaignActivity.id IN :activityIds " +
            "AND e.userId IN :userIds")
    List<CampaignActivityEntry> findByActivityIdsAndUserIds(
            @Param("activityIds") List<Long> activityIds,
            @Param("userIds") List<Long> userIds
    );

}