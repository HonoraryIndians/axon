package com.axon.core_service.service.batch;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dashboard.LTVBatch;
import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.LTVBatchRepository;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

        for (Long activityId : activityIds) {
            try {
                processActivityCohort(activityId, now);
            } catch (Exception e) {
                log.error("Failed to process activity {}: {}", activityId, e.getMessage(), e);
            }
        }

        log.info("Monthly cohort LTV batch job completed");
    }

    /**
     * 특정 캠페인 활동의 코호트 통계 처리
     */
    private void processActivityCohort(Long activityId, LocalDateTime collectedAt) {
        CampaignActivity activity = campaignActivityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));

        LocalDateTime cohortStartDate = activity.getStartDate();
        Instant startInstant = cohortStartDate.atZone(ZoneId.systemDefault()).toInstant();

        log.info("Processing cohort for activity {}: {}", activityId, activity.getName());

        // 1. 첫 구매 고객 조회 (코호트 정의)
        List<Purchase> firstPurchases = purchaseRepository.findFirstPurchasesByActivityAndPeriod(
                activityId,
                startInstant,
                Instant.now()
        );

        if (firstPurchases.isEmpty()) {
            log.warn("No first purchases found for activity {}", activityId);
            return;
        }

        List<Long> cohortUserIds = firstPurchases.stream()
                .map(Purchase::getUserId)
                .distinct()
                .collect(Collectors.toList());

        int cohortSize = cohortUserIds.size();
        BigDecimal avgCac = calculateAvgCac(activity.getBudget(), cohortSize);

        log.info("Cohort size: {} customers, Avg CAC: {}", cohortSize, avgCac);

        // 2. 해당 고객들의 모든 구매 이력 조회
        List<Purchase> allPurchases = purchaseRepository.findByUserIdIn(cohortUserIds);

        // 3. 12개월간 월별 통계 계산 및 저장
        for (int monthOffset = 0; monthOffset < 12; monthOffset++) {
            LocalDateTime monthStart = cohortStartDate.plusMonths(monthOffset);
            LocalDateTime monthEnd = cohortStartDate.plusMonths(monthOffset + 1);

            // 현재 시점을 넘어선 미래 월은 건너뛰기
            if (monthStart.isAfter(LocalDateTime.now())) {
                break;
            }

            LTVBatch stat = calculateMonthlyStats(
                    activity,
                    monthOffset,
                    collectedAt,
                    cohortStartDate,
                    cohortSize,
                    avgCac,
                    cohortUserIds,
                    firstPurchases,
                    allPurchases,
                    monthStart,
                    monthEnd
            );

            // UPSERT: 기존 데이터 있으면 삭제 후 재생성
            ltvBatchRepository
                    .findByCampaignActivityIdAndMonthOffset(activityId, monthOffset)
                    .ifPresent(ltvBatchRepository::delete);

            ltvBatchRepository.save(stat);
            log.debug("Saved stats for activity {} month {}: LTV={}", activityId, monthOffset, stat.getLtvCumulative());
        }
    }

    /**
     * 월별 통계 계산
     */
    private LTVBatch calculateMonthlyStats(
            CampaignActivity activity,
            int monthOffset,
            LocalDateTime collectedAt,
            LocalDateTime cohortStartDate,
            int cohortSize,
            BigDecimal avgCac,
            List<Long> cohortUserIds,
            List<Purchase> firstPurchases,
            List<Purchase> allPurchases,
            LocalDateTime monthStart,
            LocalDateTime monthEnd
    ) {
        Instant cohortStartInstant = cohortStartDate.atZone(ZoneId.systemDefault()).toInstant();
        Instant monthStartInstant = monthStart.atZone(ZoneId.systemDefault()).toInstant();
        Instant monthEndInstant = monthEnd.atZone(ZoneId.systemDefault()).toInstant();

        // 첫 구매 시점 매핑
        Map<Long, Instant> userFirstPurchase = firstPurchases.stream()
                .collect(Collectors.toMap(
                        Purchase::getUserId,
                        Purchase::getPurchaseAt,
                        (a, b) -> a.isBefore(b) ? a : b
                ));

        // 누적 LTV (코호트 시작 ~ 현재 월 종료까지)
        BigDecimal ltvCumulative = BigDecimal.ZERO;
        Set<Long> activeUsersSet = new HashSet<>();
        int cumulativeOrders = 0;

        for (Purchase purchase : allPurchases) {
            Instant firstPurchaseTime = userFirstPurchase.get(purchase.getUserId());
            if (firstPurchaseTime == null) continue;

            // 현재 월 종료 시점 이전 구매만 포함
            if (purchase.getPurchaseAt().isBefore(monthEndInstant)) {
                BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
                ltvCumulative = ltvCumulative.add(purchaseValue);
                cumulativeOrders++;

                // 해당 월에 구매한 고객 (월별 증분)
                if (purchase.getPurchaseAt().isAfter(monthStartInstant) || purchase.getPurchaseAt().equals(monthStartInstant)) {
                    activeUsersSet.add(purchase.getUserId());
                }
            }
        }

        // 해당 월 매출 (증분)
        BigDecimal monthlyRevenue = BigDecimal.ZERO;
        int monthlyOrders = 0;

        for (Purchase purchase : allPurchases) {
            if ((purchase.getPurchaseAt().isAfter(monthStartInstant) || purchase.getPurchaseAt().equals(monthStartInstant))
                    && purchase.getPurchaseAt().isBefore(monthEndInstant)) {
                BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
                monthlyRevenue = monthlyRevenue.add(purchaseValue);
                monthlyOrders++;
            }
        }

        // LTV/CAC 비율
        BigDecimal ltvCacRatio = avgCac.compareTo(BigDecimal.ZERO) > 0
                ? ltvCumulative.divide(avgCac.multiply(BigDecimal.valueOf(cohortSize)), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 누적 이익
        BigDecimal campaignBudget = activity.getBudget() != null ? activity.getBudget() : BigDecimal.ZERO;
        BigDecimal cumulativeProfit = ltvCumulative.subtract(campaignBudget);
        boolean isBreakEven = cumulativeProfit.compareTo(BigDecimal.ZERO) >= 0;

        // 재구매 분석 (누적)
        Map<Long, Long> purchaseCountByUser = allPurchases.stream()
                .filter(p -> {
                    Instant firstTime = userFirstPurchase.get(p.getUserId());
                    return firstTime != null && p.getPurchaseAt().isBefore(monthEndInstant);
                })
                .collect(Collectors.groupingBy(Purchase::getUserId, Collectors.counting()));

        long repeatCustomers = purchaseCountByUser.values().stream()
                .filter(count -> count > 1)
                .count();

        BigDecimal repeatRate = cohortSize > 0
                ? BigDecimal.valueOf(repeatCustomers).divide(BigDecimal.valueOf(cohortSize), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal avgFrequency = cohortSize > 0
                ? BigDecimal.valueOf(cumulativeOrders).divide(BigDecimal.valueOf(cohortSize), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgOrderValue = cumulativeOrders > 0
                ? ltvCumulative.divide(BigDecimal.valueOf(cumulativeOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

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
                .activeUsers(activeUsersSet.size())
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
