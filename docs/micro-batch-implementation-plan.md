# Axon CDP 마이크로 배치 구현 계획

> **작성일**: 2025-12-05
> **목적**: DB I/O 병목을 해결하기 위한 마이크로 배치 시스템 구축
> **기술**: Spring Boot @Scheduled + ConcurrentLinkedQueue

---

## 📋 목차

1. [배경 및 목적](#배경-및-목적)
2. [우선순위 및 일정](#우선순위-및-일정)
3. [Phase 1: Kafka Consumer 배치](#phase-1-kafka-consumer-배치-진행-중)
4. [Phase 2: Purchase Handler 배치](#phase-2-purchase-handler-배치)
5. [Phase 3: LTV Batch Service 개선](#phase-3-ltv-batch-service-개선)
6. [성능 목표](#성능-목표)

---

## 배경 및 목적

### 현재 문제점
- **DB I/O 병목**: 메시지 1개당 평균 3회 DB 접근 (조회 2회 + 저장 1회)
- **분산락 경합**: 동시 처리 시 락 대기 시간 증가
- **낮은 처리량**: 현재 ~100-200 TPS, 피크 시 부족

### 마이크로 배치란?
- **정의**: 여러 작업을 짧은 시간(100ms) 또는 개수(50개) 단위로 묶어 일괄 처리
- **장점**:
  - 네트워크 왕복 90% 감소
  - DB 트랜잭션 오버헤드 50배 감소
  - 처리량 10~25배 향상

### 기술 선택
- ✅ **Spring Boot @Scheduled** (선택)
  - 가볍고 간단
  - 기존 코드베이스와 통합 용이
- ❌ Spring Batch
  - 과도하게 무거움
  - 실시간 처리에 부적합

---

## 우선순위 및 일정

| Phase | 대상 클래스 | 우선순위 | 예상 소요 | 예상 효과 | 상태 |
|-------|------------|---------|----------|----------|------|
| **Phase 1** | CampaignActivityConsumerService | 🔥 P0 | 3시간 | TPS 10~25배↑ | 🚧 진행중 |
| **Phase 2** | PurchaseHandler | 🔥 P0 | 4시간 | FCFS 동시 처리 개선 | 📅 예정 |
| **Phase 3** | CohortLtvBatchService | ⚡ P1 | 1시간 | 배치 작업 50배↑ | 📅 예정 |
| Phase 4 | UserSummaryService | 📊 P2 | 3시간 | 유저 업데이트 개선 | 🔖 선택 |
| Phase 5 | DashboardService | 📊 P2 | 4시간 | 대시보드 로딩↑ | 🔖 선택 |

**총 예상 시간**:
- 필수 (P0-P1): 8시간
- 선택 (P2): 7시간

---

## Phase 1: Kafka Consumer 배치 (진행 중)

### 📌 대상 클래스
- `CampaignActivityConsumerService`
- `FirstComeFirstServeStrategy`
- `CampaignActivityEntryService`
- `CampaignActivityEntryRepository`

### 🎯 목표
| 지표 | Before | After | 개선 |
|------|--------|-------|------|
| DB 접근 (1,000개 메시지) | 3,000회 | 60회 | **50배↓** |
| 처리량 (TPS) | 100-200 | 2,000-5,000 | **10~25배↑** |
| 분산락 경합 | 1,000회 | 0회 | **제거** |
| 레이턴시 | 즉시 | 최대 100ms | +100ms |

### 🔧 구현 방법

#### 1. 버퍼링 구조
```java
// Kafka 메시지 → 버퍼 누적
private final ConcurrentLinkedQueue<Message> buffer = new ConcurrentLinkedQueue<>();

@KafkaListener
public void consume(Message msg) {
    buffer.offer(msg);
    if (buffer.size() >= 50) flush();  // 50개 즉시 처리
}

@Scheduled(fixedDelay = 100)
public void autoFlush() {
    flush();  // 100ms마다 자동 처리
}
```

#### 2. Bulk 처리 플로우
```
버퍼 50개 누적
    ↓
타입별 그룹핑 (FCFS, LOTTERY...)
    ↓
Strategy.processBatch(50개)
    ↓
CampaignActivity bulk 조회 (1회)
    ↓
Entry bulk 조회 (1회)
    ↓
Entry bulk save (1회)
    ↓
Event bulk 발행
```

### 📝 수정 파일 목록
1. ✅ `BatchableStrategy.java` (신규) - 배치 처리 인터페이스
2. ✅ `CampaignActivityConsumerService.java` - 버퍼 + 스케줄러 추가
3. ✅ `FirstComeFirstServeStrategy.java` - processBatch() 구현
4. ✅ `CampaignActivityEntryRepository.java` - bulk 조회 메서드 추가
5. ✅ `CampaignActivityEntryService.java` - upsertBatch() 추가

### 🧪 테스트 계획
1. **단위 테스트**
   - 버퍼 동작 (50개 누적, 100ms 타임아웃)
   - 중복 메시지 제거 로직
   - Bulk 조회/저장 검증

2. **통합 테스트**
   - Kafka 메시지 1,000개 전송
   - DB 접근 횟수 측정
   - 처리 시간 측정

3. **성능 테스트**
   - JMeter: 1,000 TPS 부하 테스트
   - 레이턴시 P99 측정

---

## Phase 2: Purchase Handler 배치

### 📌 대상 클래스
- `PurchaseHandler`
- `ProductService` (재고 감소)
- `UserSummaryService` (요약 업데이트)
- `PurchaseService` (구매 생성)

### 🎯 목표
**FCFS 동시 구매 폭주 대응 핵심**

| 지표 | Before | After | 개선 |
|------|--------|-------|------|
| 구매 1,000건 처리 시간 | 10초 | 0.5초 | **20배↑** |
| DB 접근 | 3,000회 | 60회 | **50배↓** |
| 동시성 충돌 | 빈번 | 최소화 | - |

### 🔧 구현 방법

#### 1. 비동기 배치 큐
```java
@Service
public class PurchaseHandler {

    private final Queue<PurchaseInfoDto> purchaseQueue = new ConcurrentLinkedQueue<>();

    @EventListener
    public void onPurchaseEvent(PurchaseInfoDto dto) {
        purchaseQueue.offer(dto);  // 버퍼에 추가만

        if (purchaseQueue.size() >= 50) {
            processBatch();
        }
    }

    @Scheduled(fixedDelay = 100)
    public void scheduledProcess() {
        processBatch();
    }
}
```

#### 2. Bulk 처리 플로우
```
구매 이벤트 50개 누적
    ↓
Product별 재고 감소량 집계
    ↓
Bulk 재고 업데이트 (1회 SQL)
    UPDATE products
    SET stock = stock - CASE
        WHEN id = 1 THEN 30
        WHEN id = 2 THEN 20
        END
    WHERE id IN (1, 2)
    ↓
User별 구매 통계 집계
    ↓
Bulk 유저 요약 업데이트 (1회)
    ↓
Purchase bulk insert (1회)
```

### 📝 구현 단계
1. `PurchaseHandler`에 큐 + 스케줄러 추가
2. `ProductService.decreaseStockBatch()` 구현
3. `UserSummaryService.updateBatch()` 구현
4. `PurchaseRepository.saveAllOptimized()` 추가

### ⚠️ 주의사항
- **재고 관리**: Optimistic Locking (Version) 사용
- **롤백 전략**: 배치 내 1건 실패 시 전체 롤백 vs 부분 커밋
- **중복 방지**: 이벤트 중복 수신 시 멱등성 보장

---

## Phase 3: LTV Batch Service 개선

### 📌 대상 클래스
- `CohortLtvBatchService`

### 🎯 목표
**이미 배치 작업인데 개별 save 사용 중 → saveAll 적용**

| 작업 | Before | After | 개선 |
|------|--------|-------|------|
| 100개 캠페인 × 12개월 | 1,200번 save | 1번 saveAll | **1,200배↓** |
| 배치 작업 시간 | 60초 | 2초 | **30배↑** |

### 🔧 구현 방법

#### Before (현재 코드)
```java
for (int monthOffset = 0; monthOffset < 12; monthOffset++) {
    LTVBatch stat = calculateMonthlyStats(...);
    ltvBatchRepository.save(stat);  // ❌ 개별 저장
}
```

#### After (개선)
```java
List<LTVBatch> statsToSave = new ArrayList<>();

for (int monthOffset = 0; monthOffset < 12; monthOffset++) {
    LTVBatch stat = calculateMonthlyStats(...);
    statsToSave.add(stat);  // 리스트에 모음
}

ltvBatchRepository.saveAll(statsToSave);  // ✅ Bulk 저장
```

### 📝 구현 단계
1. Line 99-129: 루프 내부 save → List 누적으로 변경
2. 루프 종료 후 saveAll() 1회 호출
3. 기존 UPSERT 로직 유지 (findByCampaignActivityIdAndMonthOffset + delete)

### ⏱️ 예상 소요 시간
**30분** (가장 간단한 작업)

---

## Phase 4: UserSummaryService 배치 (선택)

### 📌 대상 클래스
- `UserSummaryService`

### 🎯 목표
유저 요약 업데이트를 배치로 모아 처리

### 🔧 구현 방법
```java
@Service
public class UserSummaryService {

    private final Map<Long, UserUpdateAction> updateBuffer = new ConcurrentHashMap<>();

    public void recordPurchase(Long userId, BigDecimal amount) {
        updateBuffer.compute(userId, (id, action) -> {
            if (action == null) action = new UserUpdateAction(userId);
            action.addPurchase(amount);
            return action;
        });
    }

    @Scheduled(fixedDelay = 100)
    public void flushUpdates() {
        List<UserUpdateAction> actions = new ArrayList<>(updateBuffer.values());
        updateBuffer.clear();

        // Bulk UPDATE
        userSummaryRepository.bulkUpdate(actions);
    }
}
```

---

## Phase 5: DashboardService 배치 (선택)

### 📌 대상 클래스
- `DashboardService`

### 🎯 목표
Elasticsearch 쿼리를 배치로 묶어 처리

### 🔧 구현 방법
```java
// Before: 50개 활동 = 50번 ES 쿼리
for (CampaignActivity activity : activities) {
    ESResult result = esClient.query(activity.getId());
}

// After: 10-20개씩 묶어서 1번 쿼리
List<Long> activityIds = activities.stream()
    .map(CampaignActivity::getId)
    .toList();

Map<Long, ESResult> results = esClient.bulkQuery(activityIds);  // 1회 쿼리
```

---

## 성능 목표

### 최종 목표 (Phase 1-3 완료 후)

| 시나리오 | Before | After | 개선 |
|---------|--------|-------|------|
| **FCFS 1,000명 동시 참여** | | | |
| - 처리 시간 | 10초 | 0.5초 | **20배↑** |
| - DB 접근 횟수 | 3,000회 | 60회 | **50배↓** |
| **코호트 배치 (100개 캠페인)** | | | |
| - 처리 시간 | 60초 | 2초 | **30배↑** |
| - DB 접근 횟수 | 1,200회 | 1회 | **1,200배↓** |
| **전체 시스템** | | | |
| - 최대 TPS | 200 | 5,000 | **25배↑** |
| - P99 레이턴시 | 100ms | 150ms | +50ms |

---

## 모니터링 계획

### 측정 지표
1. **처리량 (TPS)**
   - Prometheus: `kafka_consumer_messages_consumed_total`
   - 목표: 2,000+ TPS

2. **레이턴시**
   - Micrometer: `@Timed("campaign.batch.processing")`
   - 목표: P99 < 200ms

3. **DB 접근 횟수**
   - Hibernate Statistics: `session.getStatistics()`
   - 목표: 메시지 50개당 3회 이하

4. **배치 크기 분포**
   - Custom Metric: `batch.size.histogram`
   - 목표: 평균 40-50개

### Grafana 대시보드
```
[Micro-Batch Performance]
- Batch Size Distribution (히스토그램)
- Messages Processed (TPS)
- Batch Processing Time (ms)
- DB Query Count
```

---

## 위험 요소 및 대응

| 위험 | 영향 | 확률 | 대응 방안 |
|------|------|------|-----------|
| **배치 처리 중 에러** | 데이터 유실 | 중 | Dead Letter Queue + 재시도 |
| **메모리 부족** | OOM | 하 | 배치 크기 50개 제한 |
| **레이턴시 증가** | 사용자 경험 | 하 | 100ms 타임아웃 엄수 |
| **중복 메시지** | 데이터 중복 | 중 | 멱등성 보장 (UPSERT) |
| **순서 보장** | 데이터 정합성 | 하 | Kafka 파티션 키 사용 |

---

## 참고 자료

### 산업 표준 배치 크기
- **Kafka**: 50-100개 (레이턴시 < 100ms)
- **Elasticsearch**: 100-500개 (Bulk API)
- **MySQL JDBC**: 50-100개 (rewriteBatchedStatements=true)

### 성능 벤치마크
- [MySQL Bulk Insert Performance](https://dev.mysql.com/doc/refman/8.0/en/insert-optimization.html)
- [Spring Batch vs Micro-batching](https://www.baeldung.com/spring-batch)
- [Kafka Consumer Performance Tuning](https://kafka.apache.org/documentation/#consumerconfigs)

---

## 변경 이력

| 날짜 | Phase | 상태 | 담당자 |
|------|-------|------|--------|
| 2025-12-05 | Phase 1 | 🚧 진행중 | - |
| 2025-12-05 | 문서 작성 | ✅ 완료 | - |

---

**다음 단계**: Phase 1 구현 완료 → 테스트 → Phase 2 착수
