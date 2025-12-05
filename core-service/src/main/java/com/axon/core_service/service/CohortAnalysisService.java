package com.axon.core_service.service;

import com.axon.core_service.domain.dto.dashboard.CohortAnalysisResponse;
import com.axon.core_service.domain.purchase.Purchase;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cohort 분석 서비스
 * LTV (Lifetime Value), CAC (Customer Acquisition Cost), 재구매율 등 계산
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CohortAnalysisService {

    private final PurchaseRepository purchaseRepository;
    private final CampaignActivityRepository campaignActivityRepository;

    /**
     * Activity 기반 Cohort 분석
     *
     * @param activityId 분석할 Activity ID
     * @param cohortStartDate Cohort 시작일 (optional, null이면 Activity 시작일 사용)
     * @param cohortEndDate Cohort 종료일 (optional, null이면 현재까지)
     * @return Cohort 분석 결과
     */
    public CohortAnalysisResponse analyzeCohortByActivity(
            Long activityId,
            LocalDateTime cohortStartDate,
            LocalDateTime cohortEndDate
    ) {
        // Activity 정보 조회
        var activity = campaignActivityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));

        // Cohort 기간 설정
        Instant startInstant = cohortStartDate != null
                ? cohortStartDate.atZone(ZoneId.systemDefault()).toInstant()
                : activity.getStartDate().atZone(ZoneId.systemDefault()).toInstant();

        Instant endInstant = cohortEndDate != null
                ? cohortEndDate.atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();

        log.info("Analyzing cohort for activity {} from {} to {}", activityId, startInstant, endInstant);

        // 1. Cohort 정의: 해당 기간에 첫 구매한 고객들
        List<Purchase> firstPurchases = purchaseRepository.findFirstPurchasesByActivityAndPeriod(
                activityId,
                startInstant,
                endInstant
        );

        if (firstPurchases.isEmpty()) {
            log.warn("No first purchases found for activity {} in period", activityId);
            return createEmptyResponse(activityId, activity.getName(), startInstant);
        }

        // 2. Cohort 고객 목록 추출
        List<Long> cohortUserIds = firstPurchases.stream()
                .map(Purchase::getUserId)
                .distinct()
                .collect(Collectors.toList());

        log.info("Cohort size: {} customers", cohortUserIds.size());

        // 3. 해당 고객들의 모든 구매 이력 조회
        List<Purchase> allPurchases = purchaseRepository.findByUserIdIn(cohortUserIds);

        // 4. LTV 계산 (시간대별)
        Map<String, BigDecimal> ltvByPeriod = calculateLTVByPeriod(firstPurchases, allPurchases);

        // 5. CAC 계산
        BigDecimal totalBudget = activity.getBudget() != null ? activity.getBudget() : BigDecimal.ZERO;
        BigDecimal avgCAC = cohortUserIds.size() > 0
                ? totalBudget.divide(BigDecimal.valueOf(cohortUserIds.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 6. 재구매 분석
        Map<String, Object> repeatPurchaseMetrics = analyzeRepeatPurchases(cohortUserIds, allPurchases);

        // 7. 응답 생성
        return buildCohortResponse(
                activityId,
                activity.getName(),
                startInstant,
                endInstant,
                cohortUserIds.size(),
                totalBudget,
                avgCAC,
                ltvByPeriod,
                repeatPurchaseMetrics
        );
    }

    /**
     * 시간대별 LTV 계산
     */
    private Map<String, BigDecimal> calculateLTVByPeriod(
            List<Purchase> firstPurchases,
            List<Purchase> allPurchases
    ) {
        Map<String, BigDecimal> ltvMap = new HashMap<>();

        // 첫 구매 시점 매핑 (userId -> firstPurchaseTime)
        Map<Long, Instant> userFirstPurchase = firstPurchases.stream()
                .collect(Collectors.toMap(
                        Purchase::getUserId,
                        Purchase::getPurchaseAt,
                        (a, b) -> a.isBefore(b) ? a : b // 중복시 더 이른 시점 선택
                ));

        // 시간대별 LTV 집계
        BigDecimal ltv30d = BigDecimal.ZERO;
        BigDecimal ltv90d = BigDecimal.ZERO;
        BigDecimal ltv365d = BigDecimal.ZERO;
        BigDecimal ltvCurrent = BigDecimal.ZERO;

        int customerCount = userFirstPurchase.size();

        for (Purchase purchase : allPurchases) {
            Instant firstPurchaseTime = userFirstPurchase.get(purchase.getUserId());
            if (firstPurchaseTime == null) continue;

            BigDecimal purchaseValue = purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
            ltvCurrent = ltvCurrent.add(purchaseValue);

            long daysSinceFirst = Duration.between(firstPurchaseTime, purchase.getPurchaseAt()).toDays();

            if (daysSinceFirst <= 30) {
                ltv30d = ltv30d.add(purchaseValue);
            }
            if (daysSinceFirst <= 90) {
                ltv90d = ltv90d.add(purchaseValue);
            }
            if (daysSinceFirst <= 365) {
                ltv365d = ltv365d.add(purchaseValue);
            }
        }

        // 평균 LTV 계산 (고객당)
        if (customerCount > 0) {
            ltv30d = ltv30d.divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.HALF_UP);
            ltv90d = ltv90d.divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.HALF_UP);
            ltv365d = ltv365d.divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.HALF_UP);
            ltvCurrent = ltvCurrent.divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.HALF_UP);
        }

        ltvMap.put("ltv30d", ltv30d);
        ltvMap.put("ltv90d", ltv90d);
        ltvMap.put("ltv365d", ltv365d);
        ltvMap.put("ltvCurrent", ltvCurrent);

        return ltvMap;
    }

    /**
     * 재구매 분석
     */
    private Map<String, Object> analyzeRepeatPurchases(
            List<Long> cohortUserIds,
            List<Purchase> allPurchases
    ) {
        Map<String, Object> metrics = new HashMap<>();

        // userId별 구매 횟수 계산
        Map<Long, Long> purchaseCountByUser = allPurchases.stream()
                .collect(Collectors.groupingBy(
                        Purchase::getUserId,
                        Collectors.counting()
                ));

        // 재구매 고객 수 (2회 이상 구매)
        long repeatCustomers = purchaseCountByUser.values().stream()
                .filter(count -> count > 1)
                .count();

        // 재구매율
        double repeatRate = cohortUserIds.size() > 0
                ? (double) repeatCustomers / cohortUserIds.size() * 100
                : 0.0;

        // 평균 구매 빈도
        double avgFrequency = cohortUserIds.size() > 0
                ? (double) allPurchases.size() / cohortUserIds.size()
                : 0.0;

        // 평균 주문 금액 (AOV)
        BigDecimal totalRevenue = allPurchases.stream()
                .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = allPurchases.size() > 0
                ? totalRevenue.divide(BigDecimal.valueOf(allPurchases.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        metrics.put("repeatPurchaseRate", repeatRate);
        metrics.put("avgPurchaseFrequency", avgFrequency);
        metrics.put("avgOrderValue", avgOrderValue);

        return metrics;
    }

    /**
     * Cohort 응답 생성
     */
    private CohortAnalysisResponse buildCohortResponse(
            Long activityId,
            String activityName,
            Instant startInstant,
            Instant endInstant,
            int customerCount,
            BigDecimal totalBudget,
            BigDecimal avgCAC,
            Map<String, BigDecimal> ltvMap,
            Map<String, Object> repeatMetrics
    ) {
        LocalDateTime startDate = LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault());
        LocalDateTime endDate = LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault());

        BigDecimal ltv30d = ltvMap.get("ltv30d");
        BigDecimal ltv90d = ltvMap.get("ltv90d");
        BigDecimal ltv365d = ltvMap.get("ltv365d");
        BigDecimal ltvCurrent = ltvMap.get("ltvCurrent");

        // LTV/CAC 비율 계산
        Double ratio30d = calculateRatio(ltv30d, avgCAC);
        Double ratio90d = calculateRatio(ltv90d, avgCAC);
        Double ratio365d = calculateRatio(ltv365d, avgCAC);
        Double ratioCurrent = calculateRatio(ltvCurrent, avgCAC);

        return new CohortAnalysisResponse(
                "activity-" + activityId,
                activityName,
                startDate,
                endDate,
                (long) customerCount,
                totalBudget,
                avgCAC,
                ltv30d,
                ltv90d,
                ltv365d,
                ltvCurrent,
                ratio30d,
                ratio90d,
                ratio365d,
                ratioCurrent,
                (Double) repeatMetrics.get("repeatPurchaseRate"),
                (Double) repeatMetrics.get("avgPurchaseFrequency"),
                (BigDecimal) repeatMetrics.get("avgOrderValue"),
                LocalDateTime.now(),
                List.of() // monthlyDetails: 실시간 계산에서는 빈 리스트 (배치 데이터 사용 권장)
        );
    }

    /**
     * LTV/CAC 비율 계산 헬퍼
     */
    private Double calculateRatio(BigDecimal ltv, BigDecimal cac) {
        if (cac.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return ltv.divide(cac, 2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 빈 응답 생성 (데이터 없을 때)
     */
    private CohortAnalysisResponse createEmptyResponse(Long activityId, String activityName, Instant startInstant) {
        LocalDateTime startDate = LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault());
        return new CohortAnalysisResponse(
                "activity-" + activityId,
                activityName,
                startDate,
                0L,
                BigDecimal.ZERO
        );
    }
}
