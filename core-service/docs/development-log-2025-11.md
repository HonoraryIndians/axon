# Axon CDP 개발 일지 (2025.11.14 ~ 11.20)

**기간**: 2025년 11월 14일 ~ 20일 (1주일)
**목표**: Activity-level 실시간 대시보드 백엔드 구축
**코드**: +1,553줄 / -141줄

---

## ✅ 완료한 작업

### 1. 실시간 대시보드 시스템

#### Dashboard API 구현
- **3개 데이터 소스 통합**: Elasticsearch (사용자 행동) + MySQL (승인/구매) + Redis (실시간 재고)
- **퍼널 분석**: VISIT → CLICK → APPROVED → PURCHASE
- **API**: `GET /api/v1/dashboard/activity/{id}`

```java
// 응답 구조
{
  "activityId": 1,
  "overview": {
    "totalVisits": 8532,
    "totalClicks": 3421,
    "approvedCount": 456,
    "purchaseCount": 312
  },
  "funnel": [...],
  "realtime": {
    "participantCount": 456,
    "remainingStock": 22
  }
}
```

#### Elasticsearch 쿼리 최적화
**4가지 버그 수정**:
1. `_id` aggregation 제거 → `hits().total()` 사용
2. `triggerType` → `triggerType.keyword` (exact match)
3. `occurredAt` ISO 8601 → Unix epoch seconds
4. Index 이름 wildcard 제거

**교훈**: ES 쿼리 전 실제 데이터 확인 필수
```bash
curl http://localhost:9200/behavior-events/_search?size=1
```

#### SSE 실시간 스트리밍
- **기술 선택**: SSE (WebSocket X)
  - 이유: 단방향 통신만 필요, Spring MVC 간단 구현
- **갱신 주기**: 5초마다 자동 업데이트
- **리소스 관리**: onCompletion/onTimeout/onError 핸들러로 메모리 누수 방지

```java
@GetMapping(value = "/stream/activity/{activityId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamActivityDashboard(@PathVariable Long activityId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

    ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
        DashboardResponse data = dashboardService.getDashboardByActivity(...);
        emitter.send(SseEmitter.event().name("dashboard-update").data(data));
    }, 0, 5, TimeUnit.SECONDS);

    emitter.onCompletion(() -> task.cancel(true));
    return emitter;
}
```

#### 테스트 자동화
- `generate-test-events.sh`: 50명 사용자 시뮬레이션 (5초 완료)
- `verify-dashboard-data.sh`: ES 데이터 검증

---

### 2. Backend Event Publishing

**문제**: 백엔드 이벤트(로그인, 구매) Kafka 발행 시 관심사 혼재

**해결**: Spring ApplicationEvents 패턴

```java
// 1. Domain Event 발행
eventPublisher.publishEvent(new UserLoginEvent(userId, username, ...));

// 2. Listener에서 Kafka 전송
@EventListener
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleUserLogin(UserLoginEvent event) {
    BehaviorEventDTO dto = eventFactory.createLoginEvent(event);
    kafkaTemplate.send("axon.event.raw", dto);
}
```

**핵심**: 트랜잭션 커밋 후 이벤트 발행 → 데이터 일관성 보장

---

## 🚀 향후 계획

### Phase 1: 대시보드 레벨별 확장 (12월 1주)

#### Level 0: 전사 Overview
```
Today's Metrics: 활성 캠페인 12개, 방문 18.5K, GMV ₩8.2M
🚨 실시간 알림: [블프-아이폰] 재고 5% 남음
🏆 Top 5 / ⚠️ Bottom 5 Campaigns
🔥 24시간 트래픽 히트맵
```
**API**: `GET /api/v1/dashboard/overview`

#### Level 1: 캠페인 비교
```
📊 캠페인별 퍼널 전환율 비교 (Bar Chart)
📋 캠페인 성과 테이블 (ROAS, GMV, 전환율)
```
**API**: `GET /api/v1/dashboard/campaigns/compare`

#### Level 2: 캠페인 내 Activity 비교
```
전체 Overview (모든 Activity 합산)
🎯 Activity별 성과 비교 테이블
```
**API**: `GET /api/v1/dashboard/campaign/{id}`

#### Level 3: Activity 실시간 (✅ 완료)
**API**: `GET /api/v1/dashboard/activity/{id}`

---

### Phase 2: 프론트엔드 구현 (12월 2주)

**우선순위**:
1. Activity 실시간 대시보드 (Thymeleaf + Chart.js)
2. 캠페인 내 Activity 비교
3. 전사 Overview
4. 캠페인 비교

**디자인 레퍼런스**:
- Grafana (실시간 모니터링)
- Mixpanel (퍼널 시각화)
- Google Analytics 4 (필터 UI)

---

### Phase 3: 성능 최적화 (12월 3~4주)

#### 1. Redisson 분산 락 (P0 - 긴급)
**문제**: Redis check-then-act로 Race Condition 발생 → Over-booking

**해결**:
```java
@DistributedLock(key = "#activityId")
public ReservationResult reserve(Long activityId, Long userId) {
    RLock lock = redissonClient.getLock("activity:" + activityId);

    if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
        try {
            // Atomic 영역
            Boolean isNew = redisTemplate.opsForSet().add(...);
            Long order = redisTemplate.opsForValue().increment(...);
            kafkaTemplate.send(...);
        } finally {
            lock.unlock();
        }
    }
}
```

**예상 효과**: FCFS 정확도 100%

---

#### 2. Virtual Threads (P1 - 단기)
**문제**: Tomcat 200개 스레드 제한 → 동시 200 req만 처리

**WebFlux vs Virtual Threads**:
| 항목 | WebFlux | Virtual Threads |
|------|---------|-----------------|
| 처리량 | 8,000+ | 8,000+ |
| 코드 변경 | 전면 재작성 | 설정만 |
| 기존 코드 재사용 | 0% | 99% |

**선택**: Virtual Threads (JDK 21)

**구현**:
```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

```java
@Bean
public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutor() {
    return protocolHandler -> {
        protocolHandler.setExecutor(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    };
}
```

**예상 효과**: 200 req/s → 1,600+ req/s (8배 향상)

---

#### 3. ZGC 튜닝 (P2 - 중기)
**문제**: G1 GC p99 pause ~50ms → latency spike

**해결**:
```bash
-XX:+UseZGC
-XX:+ZGenerational
-Xms4g -Xmx4g
```

**예상 효과**: GC pause 50ms → <1ms

---

## 📊 타임라인

```
✅ 2025-11-18 ~ 11-20
   └─ Dashboard API + SSE + Backend Events

🔄 2025-12-01 ~ 12-07
   └─ 레벨별 API + 프론트엔드 시작

📅 2025-12-08 ~ 12-14
   └─ 프론트엔드 완성 + Redisson 분산 락

📅 2025-12-15 ~ 12-21
   └─ Virtual Threads + 성능 테스트

📅 2025-12-22 ~
   └─ 프로덕션 배포
```

---

## 💡 핵심 인사이트

### 기술 선택 원칙
- **SSE > WebSocket**: 단순함이 최고
- **Runtime 집계 > Pre-aggregation**: MVP는 간단하게
- **Virtual Threads > WebFlux**: 80/20 법칙

### 성능 최적화 우선순위
1. **정확성**: Redisson (over-booking 방지)
2. **처리량**: Virtual Threads (8배)
3. **지연시간**: ZGC (<1ms)

### 개발 교훈
- ES 쿼리 전 실제 데이터 확인 필수
- 트랜잭션 경계 명확화 (ApplicationEvents)
- 버전 호환성 사전 체크 (Kafka ↔ Connector)

---

**작성**: 2025-11-20
**작성자**: yangnail
