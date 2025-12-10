package com.axon.core_service.service.batch;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dashboard.LTVBatch;
import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.LTVBatchRepository;
import com.axon.core_service.repository.PurchaseRepository;
import com.axon.core_service.service.CohortAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CohortLtvBatchService {
    private final CampaignActivityRepository campaignActivityRepository;
    private final PurchaseRepository purchaseRepository;
    private final LTVBatchRepository ltvBatchRepository;
    private final CohortAnalysisService cohortAnalysisService;

    /**
     * 배치 작업 실행 (매월 1일 새벽 3시)
     * 전달 1일 00:00:00 ~ 이번달 1일 00:00:00.000 이전 데이터 수집
     */
    @Transactional
    public void processMonthlyCohortStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twelveMonthsAgo = now.minusMonths(12);

        log.info("Starting monthly cohort LTV batch job at {}", now);

        // 12개월 이내 시작한 캠페인 조회
        List<Long> activityIds = ltvBatchRepository
                .findActivitiesForBatchProcessing(twelveMonthsAgo, now);

        log.info("Found {} activities to process", activityIds.size());

        java.util.List<LTVBatch> batchBuffer = new java.util.ArrayList<>();
        final int BATCH_SIZE = 50;

        for (Long activityId : activityIds) {
            try {
                LTVBatch stat = processActivityCohort(activityId, now);
                if (stat != null) {
                    batchBuffer.add(stat);
                }

                if (batchBuffer.size() >= BATCH_SIZE) {
                    ltvBatchRepository.saveAll(batchBuffer);
                    ltvBatchRepository.flush();
                    batchBuffer.clear();
                    log.debug("Flushed batch of {} records", BATCH_SIZE);
                }
            } catch (Exception e) {
                log.error("Failed to process activity {}: {}", activityId, e.getMessage(), e);
            }
        }

        if (!batchBuffer.isEmpty()) {
            ltvBatchRepository.saveAll(batchBuffer);
            ltvBatchRepository.flush();
            log.debug("Flushed remaining {} records", batchBuffer.size());
        }

        log.info("Monthly cohort LTV batch job completed");
    }

    /**
     * 특정 캠페인 활동의 코호트 통계 처리 (증분 업데이트)
     * - 매월 1개의 새로운 row만 INSERT
     * - 이전 달 데이터를 재활용하여 누적 계산
     */
    private LTVBatch processActivityCohort(Long activityId, LocalDateTime collectedAt) {
        CampaignActivity activity = campaignActivityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));

        LocalDateTime cohortStartDate = activity.getStartDate();

        // 1. 기존 통계 조회
        List<LTVBatch> existingStats = ltvBatchRepository
                .findByCampaignActivityIdOrderByMonthOffsetAsc(activityId);

        // 2. 마지막 month_offset 찾기
        int lastOffset = existingStats.stream()
                .mapToInt(LTVBatch::getMonthOffset)
                .max()
                .orElse(-1);

        int newOffset = lastOffset + 1;

        // 3. 조기 종료 조건
        if (newOffset >= 12) {
            log.debug("Activity {} already has 12 months of data", activityId);
            return null;
        }

        // 해당 월이 아직 도래하지 않았다면 계산 안 함
        LocalDateTime newMonthEnd = cohortStartDate.plusMonths(newOffset + 1);
        if (newMonthEnd.isAfter(collectedAt)) {
            log.debug("Activity {} month {} not yet reached", activityId, newOffset);
            return null;
        }

        // 4. 코호트 정의 (첫 구매 고객 목록) - 전체 기간
        List<Purchase> firstPurchases = purchaseRepository.findFirstPurchasesByActivityAndPeriod(
                activityId,
                cohortStartDate,
                LocalDateTime.now()
        );

        if (firstPurchases.isEmpty()) {
            log.warn("No first purchases found for activity {}", activityId);
            return null;
        }

        List<Long> cohortUserIds = firstPurchases.stream()
                .map(Purchase::getUserId)
                .distinct()
                .collect(Collectors.toList());

        int cohortSize = cohortUserIds.size();
        BigDecimal avgCac = calculateAvgCac(activity.getBudget(), cohortSize);

        // 5. 통계 계산 (증분 or 전체)
        LTVBatch newStat;
        if (newOffset == 0) {
            // 첫 달: 전체 계산
            newStat = calculateFullStats(
                    activity,
                    newOffset,
                    collectedAt,
                    cohortStartDate,
                    cohortSize,
                    avgCac,
                    cohortUserIds,
                    firstPurchases
            );
        } else {
            // 2달 이후: 증분 계산
            LTVBatch prevStat = existingStats.get(existingStats.size() - 1);
            newStat = calculateIncrementalStats(
                    activity,
                    newOffset,
                    collectedAt,
                    cohortStartDate,
                    cohortSize,
                    avgCac,
                    cohortUserIds,
                    firstPurchases,
                    prevStat
            );
        }

        log.info("Calculated stats for activity {} month {}", activityId, newOffset);
        return newStat;
    }

    /**
     * 전체 통계 계산 (offset = 0일 때)
     */
    private LTVBatch calculateFullStats(
            CampaignActivity activity,
            int monthOffset,
            LocalDateTime collectedAt,
            LocalDateTime cohortStartDate,
            int cohortSize,
            BigDecimal avgCac,
            List<Long> cohortUserIds,
            List<Purchase> firstPurchases
    ) {
        LocalDateTime monthStart = cohortStartDate.plusMonths(monthOffset);
        LocalDateTime monthEnd = cohortStartDate.plusMonths(monthOffset + 1);

        // 해당 고객들의 모든 구매 이력 조회
        List<Purchase> allPurchases = purchaseRepository.findByUserIdIn(cohortUserIds);

        // 월별 증분 데이터 (해당 월에만 발생)
        BigDecimal monthlyRevenue = BigDecimal.ZERO;
        int monthlyOrders = 0;
        Set<Long> activeUsersSet = new HashSet<>();

        for (Purchase purchase : allPurchases) {
            if ((purchase.getPurchaseAt().isAfter(monthStart) || purchase.getPurchaseAt().equals(monthStart))
                    && purchase.getPurchaseAt().isBefore(monthEnd)) {
                BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
                monthlyRevenue = monthlyRevenue.add(purchaseValue);
                monthlyOrders++;
                activeUsersSet.add(purchase.getUserId());
            }
        }

        // 누적 LTV (코호트 시작 ~ 현재 월 종료)
        BigDecimal ltvCumulative = BigDecimal.ZERO;
        for (Purchase purchase : allPurchases) {
            if (purchase.getPurchaseAt().isBefore(monthEnd)) {
                BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
                ltvCumulative = ltvCumulative.add(purchaseValue);
            }
        }

        return buildLTVBatch(
                activity,
                monthOffset,
                collectedAt,
                cohortStartDate,
                cohortSize,
                avgCac,
                cohortUserIds,
                allPurchases,
                ltvCumulative,
                monthlyRevenue,
                monthlyOrders,
                activeUsersSet.size()
        );
    }

    /**
     * 증분 통계 계산 (offset > 0일 때)
     * - 이전 달 누적 데이터 재활용
     * - 이번 달 증분만 계산
     */
    private LTVBatch calculateIncrementalStats(
            CampaignActivity activity,
            int monthOffset,
            LocalDateTime collectedAt,
            LocalDateTime cohortStartDate,
            int cohortSize,
            BigDecimal avgCac,
            List<Long> cohortUserIds,
            List<Purchase> firstPurchases,
            LTVBatch prevStat
    ) {
        LocalDateTime monthStart = cohortStartDate.plusMonths(monthOffset);
        LocalDateTime monthEnd = cohortStartDate.plusMonths(monthOffset + 1);

        // 이번 달 구매 데이터만 조회 (증분 - 코호트 유저 전체 대상)
        List<Purchase> monthlyPurchases = purchaseRepository.findByUserIdInAndPeriod(
                cohortUserIds,
                monthStart,
                monthEnd
        );

        // 월별 증분 데이터
        BigDecimal monthlyRevenue = BigDecimal.ZERO;
        int monthlyOrders = 0;
        Set<Long> activeUsersSet = new HashSet<>();

        for (Purchase purchase : monthlyPurchases) {
            // 코호트 고객인지 확인
            if (cohortUserIds.contains(purchase.getUserId())) {
                BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
                monthlyRevenue = monthlyRevenue.add(purchaseValue);
                monthlyOrders++;
                activeUsersSet.add(purchase.getUserId());
            }
        }

        // 누적 LTV = 이전 달 누적 + 이번 달 증분
        BigDecimal ltvCumulative = prevStat.getLtvCumulative().add(monthlyRevenue);

        // 재구매 분석 (전체 조회 필요)
        List<Purchase> allPurchases = purchaseRepository.findByUserIdIn(cohortUserIds);

        return buildLTVBatch(
                activity,
                monthOffset,
                collectedAt,
                cohortStartDate,
                cohortSize,
                avgCac,
                cohortUserIds,
                allPurchases,
                ltvCumulative,
                monthlyRevenue,
                monthlyOrders,
                activeUsersSet.size()
        );
    }

    /**
     * LTVBatch 엔티티 생성 (공통 로직)
     */
    private LTVBatch buildLTVBatch(
            CampaignActivity activity,
            int monthOffset,
            LocalDateTime collectedAt,
            LocalDateTime cohortStartDate,
            int cohortSize,
            BigDecimal avgCac,
            List<Long> cohortUserIds,
            List<Purchase> allPurchases,
            BigDecimal ltvCumulative,
            BigDecimal monthlyRevenue,
            int monthlyOrders,
            int activeUsers
    ) {
        // LTV/CAC 비율
        BigDecimal ltvCacRatio = avgCac.compareTo(BigDecimal.ZERO) > 0
                ? ltvCumulative.divide(avgCac.multiply(BigDecimal.valueOf(cohortSize)), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 누적 이익
        BigDecimal campaignBudget = activity.getBudget() != null ? activity.getBudget() : BigDecimal.ZERO;
        BigDecimal cumulativeProfit = ltvCumulative.subtract(campaignBudget);
        boolean isBreakEven = cumulativeProfit.compareTo(BigDecimal.ZERO) >= 0;

        // 재구매 분석 (CohortAnalysisService 재활용)
        Map<String, Object> repeatMetrics = cohortAnalysisService.analyzeRepeatPurchases(cohortUserIds, allPurchases);
        BigDecimal repeatRate = BigDecimal.valueOf((Double) repeatMetrics.get("repeatPurchaseRate"));
        BigDecimal avgFrequency = BigDecimal.valueOf((Double) repeatMetrics.get("avgPurchaseFrequency"));
        BigDecimal avgOrderValue = (BigDecimal) repeatMetrics.get("avgOrderValue");

        return LTVBatch.builder()
                .campaignActivity(activity)
                .monthOffset(monthOffset)
                .collectedAt(collectedAt)
                .cohortStartDate(cohortStartDate)
                .cohortSize(cohortSize)
                .avgCac(avgCac)
                .ltvCumulative(ltvCumulative)
                .ltvCacRatio(ltvCacRatio)
                .cumulativeProfit(cumulativeProfit)
                .isBreakEven(isBreakEven)
                .monthlyRevenue(monthlyRevenue)
                .monthlyOrders(monthlyOrders)
                .activeUsers(activeUsers)
                .repeatPurchaseRate(repeatRate)
                .avgPurchaseFrequency(avgFrequency)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    private BigDecimal calculateAvgCac(BigDecimal budget, int cohortSize) {
        if (budget == null || cohortSize == 0) {
            return BigDecimal.ZERO;
        }
        return budget.divide(BigDecimal.valueOf(cohortSize), 2, RoundingMode.HALF_UP);
    }
}
