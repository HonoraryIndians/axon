# 코호트 분석 배치 최적화 계획 (증분 업데이트 방식)

> **작성일**: 2025-12-06
> **목표**: 증분 업데이트와 인덱싱을 통한 배치 성능 최적화 (92% 성능 개선)
> **담당**: Backend Team

---

## 1. 현황 및 문제점

### 1.1. 현재 배치 로직의 문제
*   **매달 전체 재계산**: 매월 1일마다 0~11개월 통계를 전부 다시 계산 (중복 계산)
*   **비효율적인 저장**: 각 월별 통계를 개별적으로 `findBy` → `delete` → `save` (12번 × 1000개 Activity = 12,000번 DB 호출)
*   **불필요한 전체 조회**: 증분으로 계산 가능한 필드도 전체 구매 이력 조회

**예시 (Activity A가 4개월 경과):**
```
현재 로직:
  매달 1일마다 → Month 0, 1, 2, 3을 전부 다시 계산
  DB 저장: 4개 row UPSERT (기존 삭제 + 새로 생성)

문제점:
  - Month 0, 1, 2는 이미 이전 달에 계산했음 (중복 계산!)
  - 전체 구매 이력 조회 × 4번 (불필요한 중복 조회)
```

---

## 2. 개선 전략

### 2.1. 증분 업데이트 (Incremental Update)

**핵심 아이디어**: 매달 새로운 month_offset만 계산하고, 이전 달 데이터를 재활용

```
개선된 로직:
  5월 1일 배치 → Month 4만 계산 (이전 달 데이터 재활용)
  DB 저장: 1개 row INSERT

효과:
  - 계산 횟수: 4개월 → 1개월 (75% 감소)
  - DB 저장: 4번 UPSERT → 1번 INSERT (75% 감소)
  - 전체 조회: 4번 → 1번 (75% 감소)
```

### 2.2. 필드별 계산 전략

LTVBatch의 13개 필드를 증분 가능 여부에 따라 분류:

#### ✅ 증분 업데이트 가능 (8개 필드)

| 필드 | 타입 | 증분 계산 방법 |
|------|------|---------------|
| **monthlyRevenue** | 월별 증분 | 해당 달 구매만 조회<br>`WHERE purchase_at BETWEEN monthStart AND monthEnd` |
| **monthlyOrders** | 월별 증분 | 해당 달 주문 수만 계산 |
| **activeUsers** | 월별 증분 | 해당 달 구매 고객만 조회 |
| **ltvCumulative** | 누적 | `prevMonth.ltvCumulative + monthlyRevenue` |
| **ltvCacRatio** | 누적 파생 | `ltvCumulative / (avgCac × cohortSize)` |
| **cumulativeProfit** | 누적 파생 | `ltvCumulative - campaignBudget` |
| **isBreakEven** | 누적 파생 | `cumulativeProfit >= 0` |
| **cohortStartDate, cohortSize, avgCac** | 고정값 | 이전 달 그대로 복사 |

#### ❌ 전체 조회 필요 (3개 필드)

| 필드 | 전체 조회 이유 |
|------|---------------|
| **repeatPurchaseRate** | 2회 이상 구매한 고객 수 재계산 필요<br>이전 달까지 1회 → 이번 달 2회 시 재구매 고객으로 전환 |
| **avgPurchaseFrequency** | 전체 구매 횟수 / 코호트 크기 |
| **avgOrderValue** | 전체 매출 / 전체 주문 수 |

**전략**: 전체 조회는 1회만 수행하여 3개 필드 모두 계산 (CohortAnalysisService 재활용)

### 2.3. 인덱스 활용

**현재 Purchase 테이블 인덱스:**
```java
@Index(name = "idx_purchase_activity_lookup",
       columnList = "campaign_activity_id, purchase_type, purchase_at")
@Index(name = "idx_purchase_user_history",
       columnList = "user_id, purchase_at")
```

**쿼리 패턴별 인덱스 매칭:**
- ✅ 증분 조회 (해당 달): `idx_purchase_activity_lookup` 완벽 커버
- ✅ 전체 조회 (코호트): `idx_purchase_user_history` 완벽 커버
- ✅ 첫 구매 조회: 두 인덱스 모두 활용

**결론**: 추가 인덱스 불필요, 현재 인덱스만으로 충분

---

## 3. 상세 구현 설계

### 3.1. 데이터 흐름

```
[매월 1일 새벽 3시 배치 실행]
    ↓
[1. 12개월 이내 시작한 캠페인 활동 조회]
    ↓
[2. 각 Activity 처리 (반복)]
    ↓
┌───────────────────────────────────┐
│ processActivityCohort(activityId) │
└───────────────────────────────────┘
    ↓
[3. 기존 통계 조회]
    ↓
┌─────────────────────────┬─────────────────────────┐
│ 기존 통계 없음           │ 기존 통계 있음           │
│ (offset = -1)           │ (예: offset = 3)        │
└─────────────────────────┴─────────────────────────┘
    ↓                             ↓
[최초 생성]                    [증분 업데이트]
calculateFullStats()           calculateIncrementalStats()
    ↓                             ↓
offset=0 계산                  offset=4 계산
전체 조회 필요                  이전 달 재활용 + 해당 달만 조회
    ↓                             ↓
┌─────────────────────────────────┐
│ INSERT 1개 row                  │
└─────────────────────────────────┘
```

### 3.2. 핵심 메서드 설계

#### processActivityCohort() - 메인 로직
```java
private void processActivityCohort(Long activityId, LocalDateTime collectedAt) {
    // 1. 기존 통계 조회
    List<LTVBatch> existingStats = ltvBatchRepository
        .findByCampaignActivityIdOrderByMonthOffsetAsc(activityId);

    // 2. 마지막 offset 확인
    int lastOffset = existingStats.stream()
        .mapToInt(LTVBatch::getMonthOffset)
        .max()
        .orElse(-1);

    int newOffset = lastOffset + 1;

    // 3. 조기 종료 조건 확인
    if (newOffset >= 12) {
        log.info("Activity {} already has 12 months data", activityId);
        return;
    }

    LocalDateTime newOffsetStartDate = cohortStartDate.plusMonths(newOffset);
    if (newOffsetStartDate.isAfter(LocalDateTime.now())) {
        log.info("Activity {} has not reached month {}", activityId, newOffset);
        return;
    }

    // 4. 코호트 정의 (고정값, 캐싱 가능)
    List<Long> cohortUserIds = getCohortUserIds(activityId);

    // 5. 통계 계산 (증분 vs 전체)
    LTVBatch newStat;
    if (newOffset == 0) {
        newStat = calculateFullStats(activity, newOffset, ...);
    } else {
        LTVBatch prevStat = existingStats.get(existingStats.size() - 1);
        newStat = calculateIncrementalStats(activity, newOffset, prevStat, ...);
    }

    // 6. 저장 (1개 row INSERT)
    ltvBatchRepository.save(newStat);
}
```

#### calculateIncrementalStats() - 증분 계산
```java
private LTVBatch calculateIncrementalStats(
        CampaignActivity activity,
        int newOffset,
        LTVBatch prevMonthStat,
        ...
) {
    LocalDateTime monthStart = cohortStartDate.plusMonths(newOffset);
    LocalDateTime monthEnd = cohortStartDate.plusMonths(newOffset + 1);

    // ✅ 1. 증분 조회: 해당 달 구매만 (인덱스 활용)
    List<Purchase> monthlyPurchases = purchaseRepository
        .findByCampaignActivityIdAndPeriod(activityId, monthStart, monthEnd);

    // ✅ 2. 월별 증분 지표 계산
    BigDecimal monthlyRevenue = calculateRevenue(monthlyPurchases);
    int monthlyOrders = monthlyPurchases.size();
    int activeUsers = countDistinctUsers(monthlyPurchases);

    // ✅ 3. 누적 지표 계산 (이전 달 재활용)
    BigDecimal ltvCumulative = prevMonthStat.getLtvCumulative().add(monthlyRevenue);
    BigDecimal ltvCacRatio = ltvCumulative / (avgCac × cohortSize);
    BigDecimal cumulativeProfit = ltvCumulative - campaignBudget;
    boolean isBreakEven = cumulativeProfit >= 0;

    // ❌ 4. 전체 조회 필요 (재구매 지표)
    List<Purchase> allPurchases = purchaseRepository.findByUserIdIn(cohortUserIds);

    // ✅ 5. CohortAnalysisService 재활용 (3개 필드 계산)
    Map<String, Object> repeatMetrics = cohortAnalysisService
        .analyzeRepeatPurchases(cohortUserIds, allPurchases);

    // 6. LTVBatch 빌드 및 반환
    return LTVBatch.builder()
        .monthlyRevenue(monthlyRevenue)              // ✅ 증분
        .monthlyOrders(monthlyOrders)                // ✅ 증분
        .activeUsers(activeUsers)                    // ✅ 증분
        .ltvCumulative(ltvCumulative)                // ✅ 누적 (재활용)
        .repeatPurchaseRate(...)                     // ❌ 전체 조회
        .avgPurchaseFrequency(...)                   // ❌ 전체 조회
        .avgOrderValue(...)                          // ❌ 전체 조회
        .build();
}
```

### 3.3. 코드 재사용

**CohortAnalysisService.analyzeRepeatPurchases() 재활용:**
```java
// 변경 전: private
private Map<String, Object> analyzeRepeatPurchases(...)

// 변경 후: public (CohortLtvBatchService에서 재활용)
public Map<String, Object> analyzeRepeatPurchases(
    List<Long> cohortUserIds,
    List<Purchase> allPurchases
) {
    // 기존 로직 그대로
    // 반환: repeatPurchaseRate, avgPurchaseFrequency, avgOrderValue
}
```

---

## 4. 성능 개선 효과

### 4.1. 계산 횟수 감소

**시나리오**: 추적 중인 Activity 1,000개, 평균 경과 월 6개월

| 항목 | 현재 로직 | 개선 로직 | 개선율 |
|------|----------|----------|--------|
| **월별 계산 횟수** | 6개월 × 1,000 = 6,000회 | 1개월 × 1,000 = 1,000회 | **83% 감소** |
| **DB 저장 횟수** | 6 × 1,000 = 6,000번 UPSERT | 1,000번 INSERT | **83% 감소** |
| **전체 조회 횟수** | 6 × 1,000 = 6,000번 | 1,000번 | **83% 감소** |

### 4.2. 데이터 스캔 량 감소

**증분 조회 (해당 달만):**
```sql
-- 현재: 전체 기간 스캔 (0~6개월)
WHERE purchase_at BETWEEN '2025-01-01' AND '2025-07-01'  -- 6개월 데이터

-- 개선: 해당 달만 스캔
WHERE purchase_at BETWEEN '2025-06-01' AND '2025-07-01'  -- 1개월 데이터
```

**효과**: 데이터 스캔 량 83% 감소 (6개월 → 1개월)

### 4.3. 예상 실행 시간

**가정:**
- Activity당 코호트 크기: 평균 500명
- 1개월 구매 데이터: 평균 1,000건
- 전체 구매 이력: 평균 3,000건

| 작업 | 현재 로직 | 개선 로직 | 개선 효과 |
|------|----------|----------|-----------|
| **증분 조회** | - | 10ms × 1,000 = 10초 | - |
| **전체 조회** | 50ms × 6,000 = 300초 | 50ms × 1,000 = 50초 | **250초 절감** |
| **계산** | 20ms × 6,000 = 120초 | 20ms × 1,000 = 20초 | **100초 절감** |
| **DB 저장** | 10ms × 6,000 = 60초 | 5ms × 1,000 = 5초 | **55초 절감** |
| **총 실행 시간** | **480초 (8분)** | **85초 (1.4분)** | **82% 개선** |

---

## 5. 구현 체크리스트

### Phase 1: 코드 재사용 설정
- [ ] `CohortAnalysisService.analyzeRepeatPurchases()`를 `public`으로 변경
- [ ] `CohortLtvBatchService`에 `CohortAnalysisService` 주입

### Phase 2: 증분 로직 구현
- [ ] `processActivityCohort()` 수정
  - [ ] 기존 통계 조회 로직 추가
  - [ ] 마지막 offset 확인 로직 추가
  - [ ] 조기 종료 조건 추가 (offset >= 12, 날짜 미도달)
- [ ] `calculateIncrementalStats()` 메서드 구현
  - [ ] 해당 달만 조회 (증분 필드 5개)
  - [ ] 이전 달 재활용 (누적 필드 3개)
  - [ ] 전체 조회 (재구매 필드 3개)
  - [ ] `analyzeRepeatPurchases()` 호출
- [ ] `calculateFullStats()` 메서드 구현 (offset=0용)

### Phase 3: 기존 로직 제거
- [ ] `calculateMonthlyStats()` 메서드 제거 (12개월 반복 로직)
- [ ] UPSERT 로직 제거 (`findBy` → `delete` → `save`)

### Phase 4: 테스트 및 검증
- [ ] 단위 테스트 작성
  - [ ] offset=0 (최초 생성) 테스트
  - [ ] offset>0 (증분 업데이트) 테스트
  - [ ] offset=11 (마지막 달) 테스트
  - [ ] offset>=12 (조기 종료) 테스트
- [ ] 통합 테스트
  - [ ] 실제 데이터로 배치 실행
  - [ ] 이전 로직과 결과 비교 (정합성 검증)
- [ ] 성능 테스트
  - [ ] 1,000개 Activity 배치 실행 시간 측정

---

## 6. 주의사항 및 고려사항

### 6.1. 첫 실행 시 마이그레이션

**기존 데이터가 있는 경우:**
```
문제: 기존 DB에 0~11개월 데이터가 모두 존재 (중복)
해결: 최초 배치 실행 시 기존 데이터 정리 필요

옵션 1: 기존 데이터 전체 삭제 후 재생성
옵션 2: 마지막 offset만 남기고 나머지 삭제
```

### 6.2. 코호트 크기 변경 불가

**증분 로직의 전제 조건:**
- `cohortSize`, `avgCac`는 **고정값**
- 첫 구매 고객은 **추가만 가능, 변경 불가**

**주의**: 코호트 정의가 변경되면 전체 재계산 필요

### 6.3. 데이터 정합성

**전체 조회 필드 (3개)는 여전히 정확:**
- 매달 전체 구매 이력 재조회 → 최신 데이터 반영
- repeatPurchaseRate, avgPurchaseFrequency, avgOrderValue는 항상 정확

**증분 필드 (8개)는 이전 달 데이터에 의존:**
- 이전 달 데이터가 잘못되면 이후 모든 달 데이터 오류
- **중요**: 데이터 무결성 검증 로직 필요

---

## 7. 향후 개선 방향

### 7.1. 캐싱 도입
```java
// 코호트 정의는 변경되지 않으므로 캐싱 가능
@Cacheable(key = "#activityId")
public List<Long> getCohortUserIds(Long activityId) {
    // 첫 구매 고객 조회 (무거운 쿼리)
}
```

### 7.2. 병렬 처리
```java
// 1,000개 Activity를 병렬로 처리
activityIds.parallelStream()
    .forEach(activityId -> processActivityCohort(activityId, collectedAt));
```

### 7.3. 전체 조회 필드 증분화
```java
// cumulativeOrders 필드 추가 시 avgOrderValue도 증분 가능
@Column(name = "cumulative_orders")
private Integer cumulativeOrders;

// 계산
int cumulativeOrders = prevStat.getCumulativeOrders() + monthlyOrders;
BigDecimal avgOrderValue = ltvCumulative / cumulativeOrders;
```

---

## 8. 결론

**증분 업데이트 방식으로 전환 시:**
- ✅ **성능**: 82% 개선 (8분 → 1.4분)
- ✅ **확장성**: Activity 수 증가에도 선형 확장
- ✅ **데이터 정합성**: 전체 조회 필드는 여전히 정확
- ✅ **코드 재사용**: 기존 CohortAnalysisService 활용
- ✅ **인덱스**: 추가 인덱스 불필요

**추천**: 즉시 적용 가능, 높은 투자 대비 효과
