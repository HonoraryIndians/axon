# 마케팅 대시보드 개발계획

> **팀 공유 문서** | 최종 수정일: 2025-11-18 | 담당자: @team-dashboard
> **현재 상태**: 기반 인프라 완료, **Dashboard API 구현 완료** (CampaignActivity 레벨)

---

## 📋 프로젝트 개요

### 목표
Axon CDP 플랫폼에 **실시간 마케팅 대시보드**를 구축하여 캠페인 성과를 시각화하고, 데이터 기반 의사결정을 지원합니다.

### MVP 범위
- **퍼널 분석**: 방문 → 참여 → 승인 → 구매 단계별 전환율
- **프로모션 효과**: 프로모션 vs 일반 구매량 비교 분석  
- **실시간 모니터링**: 현재 잔여 재고, 실시간 참여자 수

### 성공 기준
- [ ] 실시간 데이터 업데이트 (< 5초 지연)
- [ ] 퍼널 전환율 정확도 99% 이상
- [ ] 대시보드 로딩 시간 < 2초
- [ ] 동시 사용자 100명 지원

---

## 🏗️ 아키텍처 설계

### 현재 데이터 플로우
```
Browser (JS tracker)                    Core-service (Backend)
        │ pageview/click events                │ purchase completion
        ▼                                      ▼
Entry-service (8081)                    Kafka: axon.event.raw
        │ validation & FCFS                    │ (통일된 이벤트 스키마)
        ├── Kafka: axon.event.raw (→ ES) ──────┤
        └── Kafka: axon.campaign-activity.command
                         │
                         ▼
              Core-service (8080)
                         │ domain logic
                         ▼
            MySQL + Redis + Elasticsearch
            (승인,구매)  (실시간)  (행동 분석)
                         │
                         ▼
              📊 Dashboard API
         (DashboardService + BehaviorEventService)
```

### 대시보드 아키텍처
```
Thymeleaf Frontend + Chart.js
                │
                ▼
    DashboardController
                │
       ┌────────┼────────┐
       ▼        ▼        ▼
   MySQL    Elasticsearch Redis
   (승인,    (방문,      (실시간
    결제)     클릭)       카운터)
```

---

## ⚡ 핵심 기능 명세

### 1. 퍼널 분석 대시보드
**시각화**: Funnel Chart (Chart.js)
- **Step 1**: 사이트 방문 (ES: behavior-events)
- **Step 2**: 참여 버튼 클릭 (ES: behavior-events) 
- **Step 3**: FCFS 승인 (MySQL: campaign_activity_entries)
- **Step 4**: 결제 완료 (MySQL: event_occurrences)

### 2. 프로모션 효과 분석
**시각화**: Dual Bar Chart (Chart.js)
- **프로모션 판매량**: 현재 진행 중 캠페인 결제 수
- **일반 구매량**: 동일 기간 비프로모션 구매 수
- **기준선**: 프로모션 시작 전 평균 구매량

### 3. 실시간 모니터링
**시각화**: KPI Cards + Real-time Counter
- **현재 잔여 재고**: Redis `campaign:{id}:remaining`
- **실시간 참여자**: Redis `campaign:{id}:participants` SET 크기
- **시간당 유입량**: ES 1시간 단위 집계

---

## 🔌 API 설계

### 통합 엔드포인트
```http
GET /api/v1/dashboard/campaign/{campaignId}
?widgets=overview,funnel,realtime
&period=7d
&startDate=2024-01-01
&endDate=2024-01-31
```

### 요청 파라미터
- **widgets**: `overview`, `funnel`, `comparison`, `realtime`
- **period**: `1d`, `7d`, `30d`, `custom`
- **startDate/endDate**: ISO 8601 format (period=custom 시 필수)

### 응답 구조
```json
{
  "campaignId": 123,
  "period": "7d",
  "timestamp": "2024-11-12T10:30:00Z",
  "data": {
    "overview": {
      "totalVisits": 1234,
      "totalParticipants": 567,
      "approvedCount": 123,
      "purchaseCount": 45,
      "conversionRate": 3.65
    },
    "funnel": {
      "steps": [
        {"name": "방문", "value": 1234, "rate": 100},
        {"name": "참여", "value": 567, "rate": 45.9},
        {"name": "승인", "value": 123, "rate": 21.7},
        {"name": "구매", "value": 45, "rate": 36.6}
      ]
    },
    "realtime": {
      "remainingStock": 55,
      "activeUsers": 12,
      "lastUpdated": "2024-11-12T10:30:00Z"
    }
  }
}
```

### 캐싱 전략
- **Static Data** (overview, funnel): Redis 5분 TTL
- **Realtime Data**: 캐싱 없음, 직접 조회
- **Cache Key**: `dashboard:{campaignId}:{widgets}:{period}:{hash}`

---

## 🗃️ 데이터 소스 매핑

| 지표 | 데이터 소스 | 쿼리 유형 | 예상 응답시간 | 비고 |
|------|------------|-----------|---------------|------|
| 방문 수 | Elasticsearch | DSL 집계 | < 200ms | `triggerType=PAGE_VIEW` |
| 참여 클릭 수 | Elasticsearch | DSL 집계 | < 200ms | `triggerType=CLICK` |
| 승인 수 | MySQL | JPA Query | < 100ms | `campaign_activity_entries` |
| **결제 수** | **Elasticsearch** | **DSL 집계** | **< 200ms** | **`triggerType=PURCHASE`** ✅ 2025-11-18 확정 |
| 잔여 재고 | Redis | GET | < 10ms | `campaign:{id}:remaining` |
| 실시간 참여자 | Redis | SCARD | < 10ms | `campaignActivity:{id}:participants` |

> **아키텍처 결정사항 (2025-11-18)**:
> - 구매 이벤트는 **Elasticsearch에서 조회**하여 분석 편의성과 확장성 확보
> - MySQL `event_occurrences`는 필터링 전용으로 사용 (향후 `Purchase`로 리네이밍 예정)
> - 백엔드 구매 완료 시 Kafka `axon.event.raw`로 통일된 스키마 이벤트 발행 (구현 예정)

---

## 🚀 구현 단계별 계획

### Phase 1: 백엔드 인프라 구축 ✅ 완료 (2025-11-18)
**기간**: 1주차 | **담당자**: @backend-dev

#### 서비스 레이어 구현
- [x] `DashboardService` 핵심 로직 구현 ✅
- [x] `CampaignMetricsService` - MySQL 집계 쿼리 ✅
- [x] `BehaviorEventService` - Elasticsearch DSL 쿼리 ✅
- [x] `RealtimeMetricsService` - Redis 실시간 조회 ✅
- [ ] `DashboardCacheService` - Redis 캐싱 로직 (보류: 실데이터 테스트 후)

#### 데이터 처리 로직
- [x] MySQL 복합 쿼리 작성 (JPA + @Query) ✅
- [x] Elasticsearch 집계 쿼리 구현 ✅
- [x] Redis 카운터 조회 최적화 ✅
- [x] 병렬 처리 로직 (CompletableFuture) ✅

### Phase 2: API 컨트롤러 및 DTO ✅ 완료 (2025-11-18)
**기간**: 1주차 | **담당자**: @api-dev

#### 컨트롤러 구현
- [x] `DashboardController` REST 엔드포인트 ✅
- [x] 요청 파라미터 검증 (Validation) ✅
- [x] 예외 처리 및 에러 응답 ✅
- [ ] API 문서화 (Swagger) (보류)

#### DTO 및 응답 객체
- [x] `DashboardRequest` - 요청 DTO ✅
- [x] `DashboardResponse` - 응답 DTO ✅
- [x] `OverviewData`, `FunnelData`, `RealtimeData` ✅
- [x] JSON 직렬화 최적화 ✅

### Phase 3: 프론트엔드 대시보드
**기간**: 1.5주차 | **담당자**: @frontend-dev

#### Thymeleaf 템플릿
- [ ] 대시보드 메인 페이지 레이아웃
- [ ] 위젯별 컴포넌트 분리
- [ ] 반응형 디자인 (Bootstrap/Tailwind)
- [ ] 로딩 상태 및 에러 처리

#### Chart.js 시각화  
- [ ] Funnel Chart 구현
- [ ] Dual Bar Chart (프로모션 vs 일반)
- [ ] KPI Cards (실시간 지표)
- [ ] 차트 인터랙션 (툴팁, 드릴다운)

#### 실시간 업데이트
- [ ] JavaScript 폴링 또는 WebSocket
- [ ] 실시간 데이터 자동 갱신
- [ ] 성능 최적화 (throttling)

### Phase 4: 테스트 및 최적화
**기간**: 0.5주차 | **담당자**: @전체팀

#### 성능 테스트
- [ ] API 응답 시간 측정
- [ ] 동시 요청 부하 테스트
- [ ] 캐시 히트율 모니터링
- [ ] 메모리 사용량 프로파일링

#### 통합 테스트
- [ ] End-to-End 테스트 작성
- [ ] 데이터 정합성 검증
- [ ] 브라우저 호환성 테스트
- [ ] 사용자 시나리오 테스트

---

## 📊 Cohort Analysis (고객 생애 가치 분석)

> **추가일**: 2025-11-23 | **상태**: ✅ 구현 완료
> **목적**: FCFS 캠페인을 통해 유입된 고객 코호트의 장기적 가치(LTV)를 분석하여 마케팅 ROI 평가

### 핵심 지표

- **LTV (Lifetime Value)**: 고객 생애 가치 (30일/90일/365일/현재)
- **CAC (Customer Acquisition Cost)**: 고객 획득 비용 (캠페인 예산 / 유입 고객 수)
- **LTV/CAC Ratio**: 마케팅 효율성 지표 (목표: > 3.0이 건강)
- **Repeat Purchase Rate**: 재구매율 (2회 이상 구매한 고객 비율)
- **Average Purchase Frequency**: 평균 구매 빈도
- **Average Order Value**: 평균 주문 금액

### 코호트 정의

특정 시점(Activity 기간)에 같은 캠페인으로 유입된 고객 그룹을 추적:

```
코호트 = Activity를 통해 첫 구매한 고객들
└─ 추적 기간: 첫 구매일 기준 30일/90일/365일/현재까지
└─ 재구매 포함: 동일 userId의 모든 구매 이력
```

### Architecture

```
┌─────────────────────────────────────────────────┐
│ Frontend: /admin/dashboard/cohort/{activityId}  │
│ (cohort-dashboard.html + Chart.js)              │
└────────────────────┬────────────────────────────┘
                     │ GET /api/v1/dashboard/cohort/activity/{id}
                     ↓
┌─────────────────────────────────────────────────┐
│ DashboardController                              │
│  └─ CohortAnalysisService                       │
└────────┬───────────────────────┬─────────────┬──┘
         ↓                       ↓             ↓
┌────────────────┐   ┌─────────────────┐   ┌──────────┐
│ Purchase       │   │ CampaignActivity│   │ MySQL    │
│ Repository     │   │ Repository      │   │ purchases│
│ (Custom Query) │   │                 │   │ 테이블   │
└────────────────┘   └─────────────────┘   └──────────┘
```

### 핵심 쿼리: 첫 구매 고객 추출

```sql
-- PurchaseRepository.findFirstPurchasesByActivityAndPeriod()
-- 각 유저의 첫 구매만 추출 (코호트 정의)
SELECT p.* FROM purchases p
INNER JOIN (
    SELECT user_id, MIN(purchased_at) as first_purchase
    FROM purchases
    WHERE campaign_activity_id = :activityId
    AND purchased_at >= :startDate
    AND purchased_at < :endDate
    GROUP BY user_id
) first ON p.user_id = first.user_id
      AND p.purchase_at = first.first_purchase
WHERE p.campaign_activity_id = :activityId;
```

### LTV 계산 로직

```java
// 1. 첫 구매 시점 매핑
Map<Long, Instant> userFirstPurchase = firstPurchases.stream()
    .collect(Collectors.toMap(
        Purchase::getUserId,
        Purchase::getPurchaseAt
    ));

// 2. 모든 구매에 대해 경과 일수 계산 후 시간대별 집계
for (Purchase purchase : allPurchases) {
    long daysSinceFirst = Duration.between(
        userFirstPurchase.get(purchase.getUserId()),
        purchase.getPurchaseAt()
    ).toDays();

    if (daysSinceFirst <= 30) ltv30d += purchase.getPrice();
    if (daysSinceFirst <= 90) ltv90d += purchase.getPrice();
    // ...
}

// 3. 고객당 평균 LTV로 변환
ltv30d = ltv30d / customerCount;
```

### 기술 스택 선정: MySQL

#### ✅ 선정 이유

1. **기존 인프라 활용**: OLTP 워크로드와 동일 DB 사용으로 복잡도 최소화
2. **데모/초기 단계 충분**: ~100만 건 이하에서 1~3초 응답 (인덱스 최적화 시)
3. **빠른 개발 속도**: Spring Data JPA 완벽 호환, 마이그레이션 불필요

#### ⚠️ 트레이드오프 (알고 선택한 제약사항)

| 항목 | MySQL (현재) | PostgreSQL (대안) | 판단 |
|------|--------------|-------------------|------|
| 복잡한 집계 성능 | Subquery + JOIN | Window Function | 초기 규모에선 차이 미미 |
| Materialized View | 수동 구현 필요 | Native 지원 | 배치 테이블로 대체 가능 |
| 분석 쿼리 최적화 | 제한적 | CTE, Window Func 강력 | 인덱스로 보완 |
| 확장성 | Read Replica 필요 | OLAP 워크로드 최적화 | 초기엔 불필요 |

**결론**: 프로젝트 규모(데모/MVP)에서는 MySQL로 충분. 필요 시 PostgreSQL/ClickHouse 마이그레이션 가능.

### 성능 최적화

#### 필수 인덱스 (Level 1 최적화)

```sql
-- 코호트 쿼리 최적화용 (10배 성능 향상)
CREATE INDEX idx_purchases_cohort
ON purchases(campaign_activity_id, purchased_at, user_id);

-- 재구매 조회 최적화용 (5배 성능 향상)
CREATE INDEX idx_purchases_user_time
ON purchases(user_id, purchased_at);
```

**성능 개선 효과**:
- Subquery GROUP BY: Full Scan → Index Scan (10배)
- findByUserIdIn(): Table Scan → Index Seek (5배)
- 전체 응답 시간: 5~10초 → **1~2초**

#### 예상 성능 (100만 건 기준)

| 지표 | 값 |
|------|-----|
| purchases 테이블 크기 | ~100MB |
| 인덱스 크기 | ~40MB |
| 평균 코호트 크기 | 50~100명 |
| 평균 구매 횟수/고객 | 2~5회 |
| 쿼리 응답 시간 | 1~3초 |
| 메모리 사용량 | ~100KB/요청 |

### 확장 전략 (3단계)

#### Phase 1: 현재 (데모/MVP) ✅
- MySQL 단일 DB
- 실시간 집계 (인덱스 최적화)
- 단순 캐싱
- **적용 시점**: ~100만 건, 동시 사용자 < 10명

#### Phase 2: 성장기
- 배치 집계 테이블 (cohort_analysis_cache)
- Spring Batch 야간 작업
- Redis 캐시 레이어 (TTL: 6시간)
- **적용 시점**: 100만~1000만 건, 동시 사용자 10~100명

```sql
-- 집계 테이블 예시
CREATE TABLE cohort_analysis_cache (
    campaign_activity_id BIGINT PRIMARY KEY,
    ltv_30d DECIMAL(15,2),
    ltv_90d DECIMAL(15,2),
    repeat_purchase_rate DOUBLE,
    calculated_at DATETIME,
    INDEX idx_calculated_at (calculated_at)
);
```

#### Phase 3: 대규모 서비스
- PostgreSQL OLAP DB 분리
- Materialized View
- ClickHouse/BigQuery (선택)
- **적용 시점**: 1000만 건 이상, 실시간 분석 필요

```sql
-- PostgreSQL Materialized View
CREATE MATERIALIZED VIEW cohort_ltv_mv AS
SELECT
    campaign_activity_id,
    COUNT(DISTINCT user_id) as total_customers,
    SUM(CASE WHEN days_since_first <= 30 THEN price ELSE 0 END) /
        COUNT(DISTINCT user_id) as ltv_30d
FROM (
    SELECT p.*,
           EXTRACT(DAY FROM (p.purchased_at - fp.first_purchase)) as days_since_first
    FROM purchases p
    INNER JOIN (
        SELECT user_id, MIN(purchased_at) as first_purchase
        FROM purchases GROUP BY user_id
    ) fp ON p.user_id = fp.user_id
) cohort_data
GROUP BY campaign_activity_id;
```

### API 엔드포인트

```http
GET /api/v1/dashboard/cohort/activity/{activityId}
?startDate=2024-11-01T00:00:00
&endDate=2024-11-30T23:59:59
```

**응답 예시**:
```json
{
  "cohortId": "activity-1",
  "cohortName": "블랙프라이데이 FCFS",
  "cohortStartDate": "2024-11-23T00:00:00",
  "cohortEndDate": "2024-11-30T23:59:59",
  "totalCustomers": 42,
  "totalAcquisitionCost": 5000000,
  "avgCAC": 119047.62,
  "ltv30d": 1590000,
  "ltv90d": 2390000,
  "ltv365d": 2390000,
  "ltvCurrent": 2390000,
  "ltvCacRatio30d": 13.35,
  "ltvCacRatio90d": 20.07,
  "ltvCacRatio365d": 20.07,
  "ltvCacRatioCurrent": 20.07,
  "repeatPurchaseRate": 66.67,
  "avgPurchaseFrequency": 2.14,
  "avgOrderValue": 1116667,
  "calculatedAt": "2024-11-23T15:30:00"
}
```

### UI 구성

**별도 페이지 전략**:
- **실시간 대시보드**: `/admin/dashboard/{activityId}` (현재 진행형 이벤트 모니터링)
- **코호트 대시보드**: `/admin/dashboard/cohort/{activityId}` (장기 가치 분석)
- **페이지 간 링크**: "고객 LTV 분석 보기" 버튼으로 연결

**시각화 컴포넌트**:
- LTV 성장 차트 (Chart.js Line Chart): LTV vs CAC 비교
- 재구매 메트릭 카드
- LTV/CAC 비율 해석 (자동 색상 구분)
  - < 1.0: 빨강 (손실)
  - 1.0~3.0: 노랑 (주의)
  - > 3.0: 녹색 (우수)

### 테스트 전략

#### 단위 테스트
- ✅ `CohortAnalysisServiceTest` (7개 테스트 케이스)
  - 단일/다수 고객 시나리오
  - LTV 시간대별 계산 검증
  - 재구매율 계산 검증
  - 빈 코호트 처리
  - 예외 처리

#### 통합 테스트 시나리오
1. **k6 부하 테스트**: FCFS 참여 시뮬레이션 (42명 고객 생성)
2. **LTV 시뮬레이션 스크립트**: 동일 고객의 미래 구매 데이터 생성
3. **대시보드 검증**: 코호트 분석 결과 확인

### 잠재적 병목 및 해결책

#### 🔴 문제: OLTP/OLAP 혼재

**시나리오**: 실시간 주문 테이블에 분석 쿼리 동시 실행
- 영향: Row-level lock, 커넥션 풀 고갈

**해결책 (우선순위별)**:
1. **인덱스 최적화** (Priority 1, 즉시 적용)
2. **Read Replica 분리** (Priority 2)
3. **배치 집계 테이블** (Priority 3, 100만 건 이상 시)

#### 🔴 문제: 메모리 사용량

**시나리오**: 대규모 코호트 (10,000명 이상)
- 메모리: 100명 × 5회 = 500건 × 200bytes = 100KB (안전)
- 위험: 10,000명 × 5회 = 50,000건 = 10MB (주의)

**해결책**:
- 배치 처리로 전환
- 페이지네이션 또는 스트리밍 처리

### FAQ

**Q1. 왜 PostgreSQL이 아닌 MySQL을 선택했나?**
- 프로젝트 규모(데모/MVP)에서는 성능 차이 미미
- 기존 OLTP 워크로드와 동일 DB 사용 → 인프라 단순화
- 필요 시 PostgreSQL로 마이그레이션 가능 (테이블 구조 호환)

**Q2. 실시간 주문 테이블에 분석 쿼리를 돌려도 괜찮은가?**
- 인덱스 최적화 필수 (Row-level lock 범위 최소화)
- Read Replica 사용 권장 (OLTP와 격리)
- 동시 사용자 제한 고려 (10명 이하, 분석 대시보드 특성)

**Q3. 데이터가 많아지면 응답 시간이 느려지지 않나?**

| 데이터 규모 | 응답 시간 | 상태 | 조치 |
|------------|----------|------|------|
| ~10만 건 | < 1초 | ✅ 양호 | 없음 |
| ~100만 건 | 1~3초 | ⚠️ 허용 | 인덱스 최적화 |
| ~1000만 건 | 5~10초 | 🔴 느림 | 배치 집계 필수 |
| 1억 건 이상 | 30초+ | 🔴 불가 | PostgreSQL/ClickHouse |

### 구현 완료 항목 (2025-11-23)

- [x] `CohortAnalysisService` - 핵심 분석 로직
- [x] `CohortAnalysisResponse` DTO
- [x] `PurchaseRepository` 커스텀 쿼리 (첫 구매, 재구매)
- [x] `DashboardController` - REST API 엔드포인트
- [x] `DashboardViewController` - 코호트 뷰 페이지
- [x] `cohort-dashboard.html` - Thymeleaf 템플릿
- [x] `cohort-dashboard.js` - Chart.js 시각화
- [x] `CohortAnalysisServiceTest` - 단위 테스트 (7개)
- [x] 기술 문서화 및 트레이드오프 정리

---

## 🔮 향후 LLM 확장 계획

### 자연어 쿼리 시스템 로드맵
**목표**: "지난 7일간 방문자 중 몇 퍼센트가 구매했어?" → 자동 응답

#### Phase A: 쿼리 파싱 서비스
- [ ] `LLMQueryService` - 자연어 쿼리 처리
- [ ] `QueryParsingService` - LLM API 연동
- [ ] `DataSourceRouterService` - 쿼리 → 데이터소스 매핑

#### Phase B: 기존 API 통합
- [ ] 기존 Dashboard 서비스들 재사용
- [ ] 동적 쿼리 실행 엔진
- [ ] 자연어 응답 생성 로직

#### Phase C: 고급 기능
- [ ] 쿼리 히스토리 및 즐겨찾기
- [ ] 맥락 기반 연속 질의
- [ ] 자동 인사이트 생성

### 예상 API 구조
```http
POST /api/v1/dashboard/campaign/{campaignId}/query
{
  "query": "지난 7일간 방문자 중 몇 퍼센트가 구매했어?",
  "language": "ko"
}
```

---

## 👥 팀 협업 가이드

### 담당자 배정
| 역할 | 담당자 | 연락처 | 주요 업무 |
|------|--------|--------|----------|
| 백엔드 리드 | @backend-lead | - | 서비스 레이어, DB 최적화 |
| API 개발 | @api-dev | - | 컨트롤러, DTO, API 문서 |
| 프론트엔드 | @frontend-dev | - | UI/UX, Chart.js, 반응형 |
| DevOps | @devops | - | 배포, 모니터링, 성능 |

### Git 브랜치 전략
```
main (운영)
├── develop (개발)
│   ├── feature/dashboard-backend-api
│   ├── feature/dashboard-frontend-charts  
│   ├── feature/dashboard-realtime-update
│   └── feature/llm-query-system
```

### 일정 관리
- **주간 스프린트**: 매주 월요일 스프린트 계획
- **데일리 스탠드업**: 매일 오전 10시 (15분)
- **리뷰 미팅**: 매주 금요일 오후 3시
- **데모 데이**: Phase별 완료 후 전체 데모

### 진행상황 체크리스트

#### 주차별 마일스톤
- [x] **1주차**: Phase 1 완료 - 백엔드 API 인프라 ✅ (2025-11-18)
- [x] **2주차**: Phase 2 완료 - REST API 및 DTO ✅ (2025-11-18)
- [ ] **3주차**: Phase 3 완료 - 프론트엔드 대시보드 (🔄 다음 단계)
- [ ] **4주차**: Phase 4 완료 - 테스트 및 배포

#### 의존성 관리 (기반 인프라)
- [x] Docker 개발 환경 설정 완료 ✅
- [x] Kafka 파이프라인 구축 완료 ✅
- [x] Elasticsearch 인덱스 구조 확정 ✅
- [ ] 테스트 데이터 준비 (샘플 캠페인, 이벤트)
- [ ] Chart.js 라이선스 및 CDN 설정

---

## 🎯 아키텍처 결정사항 (ADR)

### 1. 구매 이벤트 데이터 소싱 전략 (2025-11-18)

**결정**: 대시보드에서 구매 수는 Elasticsearch로 조회

**배경**:
- MySQL `event_occurrences` vs Elasticsearch `behavior-events` 중 선택 필요
- EventOccurrence 원래 목적: 필터링 (구매 이력 있는 유저만 참여 가능 등)
- Elasticsearch에 이미 모든 행동 이벤트 적재 완료

**장점**:
- ✅ 방문/클릭/구매를 단일 데이터소스에서 조회 (쿼리 일관성)
- ✅ ES aggregation 성능 우수 (대용량 집계 특화)
- ✅ 시간대별/세그먼트별 분석 용이
- ✅ 실시간성 확보 (Kafka → ES 파이프라인)

**트레이드오프**:
- ⚠️ 데이터 지연 가능 (Kafka Connect lag, 일반적으로 < 5초)
- ⚠️ EventOccurrence와 데이터 불일치 가능성 (eventual consistency)

**완화 방안**:
- 대시보드는 분석용이므로 5초 지연 허용 가능
- 필터링용 실시간 검증은 MySQL EventOccurrence 사용 유지

---

### 2. 백엔드 이벤트 정규화 전략 (2025-11-18)

**결정**: 백엔드 구매 완료 시 Kafka `axon.event.raw`에 통일된 스키마로 이벤트 발행

**스키마 설계**:
```json
{
  "triggerType": "PURCHASE",
  "userId": 123,
  "pageUrl": "http://backend/campaign-activity/789/purchase",  // synthetic!
  "sessionId": null,
  "userAgent": "axon-backend/1.0",
  "occurredAt": "2025-11-18T10:30:00Z",
  "properties": {
    "source": "backend",
    "activityId": 789,
    "productId": 456,
    "amount": 50000,
    "orderId": "ORD-20251118-123"
  }
}
```

**장점**:
- ✅ 프론트엔드/백엔드 이벤트 통합 조회 가능
- ✅ ES 쿼리 패턴 일관성 (pageUrl wildcard 동일 사용)
- ✅ 이벤트 소스 추적 가능 (`properties.source`)
- ✅ 확장성 (향후 다른 백엔드 이벤트 추가 용이)

**구현 계획**:
1. `BackendEventFactory` 컴포넌트 생성
2. Payment 완료 후 Kafka 발행 로직 추가
3. ES 쿼리에서 pageUrl wildcard 패턴 유지

---

### 3. Entry-service 비동기 전환 로드맵 (우선순위: 최상)

**현재 문제**:
- 2-tier validation에서 `.block()` 사용으로 Thread pool exhaustion 위험
- 동시 요청 처리 능력 제한 (TPS 병목)

**개선 계획 (3단계)**:

**Phase 1**: WebFlux 전환 (예상 효과: TPS 5-10배 증가)
- Spring MVC → Spring WebFlux 전환
- `.block()` 제거, reactive chain 구성
- Event loop 기반 비동기 처리

**Phase 2**: Heavy validation 결과 캐싱 (예상 효과: HTTP 호출 90% 감소)
- Redis에 validation 결과 캐싱 (TTL: 5분)
- Cache key: `validation:{userId}:{activityId}`
- Miss 시에만 Core-service 호출

**Phase 3**: Redis cache warming (대규모 트래픽 대응)
- 캠페인 시작 전 유저 프로필 pre-loading
- Batch job으로 eligibility 사전 계산

---

### 4. 향후 확장 계획

#### Campaign 레벨 대시보드
- 현재: CampaignActivity 단위 API만 구현
- 향후: Campaign 내 여러 Activity 집계 API
- 엔드포인트: `GET /api/v1/dashboard/campaign/{campaignId}` (전체 캠페인)

#### LLM 쿼리 시스템
- 자연어 → ES/SQL 템플릿 변환
- 기존 Dashboard 서비스 재사용
- 콘셉트 문서: `docs/dev-log-2025-11-18-dashboard-architecture.md` 참고

---

## 📚 참고 자료

### 기술 문서
- [Axon CDP 아키텍처 개요](../project-overview.md)
- [Behavior Tracker 스펙](../behavior-tracker.md)
- [Kafka Connect 설정](../flow/behavior-event-fluentd-plan.md)
- [CLAUDE.md 개발 가이드](../../CLAUDE.md)
- [개발일지 2025-11-18: 대시보드 아키텍처 결정사항](../devlog/dev-log-2025-11-18-dashboard-architecture.md) ⭐ NEW

### 외부 라이브러리
- [Chart.js 공식 문서](https://www.chartjs.org/docs/)
- [Elasticsearch DSL 가이드](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
- [Spring Boot Cache 설정](https://spring.io/guides/gs/caching/)

### 디자인 참고
- [Marketing Dashboard UI Examples](https://dribbble.com/tags/marketing_dashboard)
- [Funnel Chart Best Practices](https://blog.chartio.com/posts/what-is-a-funnel-chart)

---

## 🔄 문서 업데이트 로그

| 날짜 | 변경사항 | 작성자 |
|------|----------|--------|
| 2024-11-12 | 초기 개발계획 작성 | @team-dashboard |
| 2025-11-13 | 진행 상황 업데이트 (기반 인프라 완료 표시) | @team-dashboard |
| 2025-11-18 | **Phase 1, 2 완료 표시 / 아키텍처 결정사항 반영** | @team-dashboard |
| 2025-11-18 | - Dashboard API 구현 완료 (CampaignActivity 레벨) | |
| 2025-11-18 | - 구매 이벤트 ES 조회 전략 확정 (MySQL → ES) | |
| 2025-11-18 | - 백엔드 이벤트 정규화 전략 문서화 | |
| 2025-11-18 | - 데이터 플로우 다이어그램 업데이트 | |
| 2025-11-23 | **Cohort Analysis 기능 추가** | @team-dashboard |
| 2025-11-23 | - LTV/CAC 분석 백엔드/프론트엔드 구현 완료 | |
| 2025-11-23 | - MySQL vs PostgreSQL 기술 트레이드오프 문서화 | |
| 2025-11-23 | - 성능 최적화 전략 3단계 로드맵 수립 | |

---

**💡 이 문서는 팀 공유용이므로, 진행상황 업데이트 시 체크박스를 체크하고 커밋해주세요!**