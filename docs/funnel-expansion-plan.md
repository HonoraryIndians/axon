# Funnel Expansion Plan: RAFFLE & COUPON

## Overview

현재 시스템은 **FCFS (선착순)** 활동 타입을 지원하며, **범용 4단계 퍼널**로 리팩토링되었습니다:

```
VISIT → ENGAGE → QUALIFY → PURCHASE
```

향후 **RAFFLE (래플/추첨)** 및 **COUPON (쿠폰)** 활동 타입 추가를 위한 확장 계획입니다.

---

## Current State (FCFS)

### Funnel Mapping

| Funnel Step | FCFS Event | Description |
|-------------|------------|-------------|
| **VISIT** | `PAGE_VIEW` | 캠페인 페이지 방문 |
| **ENGAGE** | `CLICK` | 참여하기 버튼 클릭 |
| **QUALIFY** | `APPROVED` | 선착순 통과 (예약 성공) |
| **PURCHASE** | `PURCHASE` | 구매 완료 |

### Current Architecture

```
Frontend JS Tracker → Entry-service → Kafka → Elasticsearch
                              ↓
                    Backend Event (APPROVED)
```

**Key Components:**
- `FunnelStep` enum: 4단계 정의
- `BehaviorEventService`: TriggerType → FunnelStep 매핑
  - `getEngageTriggerTypes()`: `["CLICK"]`
  - `getQualifyTriggerTypes()`: `["APPROVED"]`
- `DashboardService`: 퍼널 데이터 집계

---

## Phase 2: RAFFLE (래플/추첨) 추가

### User Journey

```
1. [VISIT] 래플 페이지 방문
2. [ENGAGE] 응모하기 클릭 → 응모 정보 입력 → 제출
3. [대기] 추첨 일시까지 대기 (백그라운드 배치 처리)
4. [QUALIFY] 당첨 발표 (당첨자에게만 발생)
5. [PURCHASE] 당첨 상품 구매
```

### New Components

#### 1. CampaignActivityType 추가

```java
// common-messaging/src/main/java/com/axon/messaging/CampaignActivityType.java
public enum CampaignActivityType {
    FIRST_COME_FIRST_SERVE, // 기존
    RAFFLE,                 // 새로 추가
    COUPON                  // 새로 추가
}
```

#### 2. New TriggerTypes

```java
// Frontend events (via JS tracker)
PAGE_VIEW    // 기존
APPLY        // 새로 추가: 래플 응모
PURCHASE     // 기존

// Backend events (via domain event publisher)
WON          // 새로 추가: 래플 당첨
```

#### 3. Entry-service: RaffleController

```java
@RestController
@RequestMapping("/api/v1/raffle")
public class RaffleController {

    @PostMapping("/apply/{activityId}")
    public ResponseEntity<RaffleApplicationResult> applyRaffle(
        @PathVariable Long activityId,
        @RequestBody RaffleApplicationRequest request,
        @AuthenticationPrincipal UserDetails user) {

        // 1. 중복 응모 체크 (Redis Set)
        // 2. 응모 정보 저장 (MySQL: raffle_applications)
        // 3. RaffleAppliedEvent 발행 (Spring Events)
        // 4. BackendEventPublisher가 APPLY 이벤트를 Kafka로 발행

        return ResponseEntity.ok(result);
    }
}
```

#### 4. Core-service: RaffleDrawBatchJob

```java
@Component
public class RaffleDrawBatchJob {

    @Scheduled(cron = "0 0 * * * *") // 매 시 정각
    public void processScheduledRaffles() {
        // 1. 추첨 일시가 도래한 래플 조회
        // 2. 당첨자 추첨 (랜덤 또는 가중치)
        // 3. raffle_winners 테이블에 저장
        // 4. RaffleWonEvent 발행
        // 5. 알림 발송 (이메일/SMS)
    }
}
```

#### 5. BackendEventPublisher 확장 (entry-service)

```java
@Component
public class BackendEventPublisher {

    // 기존
    @EventListener
    public void handleReservationApproved(ReservationApprovedEvent event) {
        // APPROVED 이벤트 발행
    }

    // 새로 추가
    @EventListener
    public void handleRaffleApplied(RaffleAppliedEvent event) {
        UserBehaviorEventMessage message = adapter.toApplyEvent(event);
        kafkaTemplate.send(KafkaTopics.EVENT_RAW, message);
    }
}
```

#### 6. BackendEventPublisher 확장 (core-service)

```java
@Component
public class BackendEventPublisher {

    @EventListener
    public void handleRaffleWon(RaffleWonEvent event) {
        UserBehaviorEventMessage message = adapter.toWonEvent(event);
        kafkaTemplate.send(KafkaTopics.EVENT_RAW, message);
    }
}
```

#### 7. BehaviorEventService 업데이트

```java
private List<String> getEngageTriggerTypes() {
    return List.of(
        "CLICK",   // FCFS
        "APPLY"    // RAFFLE (추가)
    );
}

private List<String> getQualifyTriggerTypes() {
    return List.of(
        "APPROVED", // FCFS
        "WON"       // RAFFLE (추가)
    );
}
```

### Database Schema

```sql
-- 래플 응모 정보
CREATE TABLE raffle_applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    application_data JSON, -- 추가 응모 정보
    UNIQUE KEY uk_activity_user (campaign_activity_id, user_id)
);

-- 래플 당첨자
CREATE TABLE raffle_winners (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    won_at TIMESTAMP NOT NULL,
    redeemed_at TIMESTAMP,
    purchase_id BIGINT,
    INDEX idx_activity_won (campaign_activity_id, won_at)
);
```

### Funnel Mapping (RAFFLE)

| Funnel Step | RAFFLE Event | Description |
|-------------|--------------|-------------|
| **VISIT** | `PAGE_VIEW` | 래플 페이지 방문 |
| **ENGAGE** | `APPLY` | 래플 응모 |
| **QUALIFY** | `WON` | 당첨 |
| **PURCHASE** | `PURCHASE` | 당첨 상품 구매 |

---

## Phase 3: COUPON (쿠폰) 추가

### User Journey

```
1. [VISIT] 쿠폰 페이지 방문
2. [ENGAGE] 쿠폰 받기 클릭
3. [QUALIFY] 쿠폰 발급 완료 (my_coupons에 저장)
4. [PURCHASE] 쿠폰 사용해서 구매
```

### New Components

#### 1. New TriggerTypes

```java
// Frontend events
PAGE_VIEW    // 기존
CLAIM        // 새로 추가: 쿠폰 받기 클릭
PURCHASE     // 기존

// Backend events
ISSUED       // 새로 추가: 쿠폰 발급 완료
```

#### 2. Entry-service: CouponController

```java
@RestController
@RequestMapping("/api/v1/coupon")
public class CouponController {

    @PostMapping("/claim/{activityId}")
    public ResponseEntity<CouponClaimResult> claimCoupon(
        @PathVariable Long activityId,
        @AuthenticationPrincipal UserDetails user) {

        // 1. 중복 발급 체크
        // 2. 재고 체크 (Redis decrement)
        // 3. 쿠폰 발급 (MySQL: user_coupons)
        // 4. CouponIssuedEvent 발행
        // 5. BackendEventPublisher가 ISSUED 이벤트를 Kafka로 발행

        return ResponseEntity.ok(result);
    }
}
```

#### 3. BehaviorEventService 업데이트

```java
private List<String> getEngageTriggerTypes() {
    return List.of(
        "CLICK",   // FCFS
        "APPLY",   // RAFFLE
        "CLAIM"    // COUPON (추가)
    );
}

private List<String> getQualifyTriggerTypes() {
    return List.of(
        "APPROVED", // FCFS
        "WON",      // RAFFLE
        "ISSUED"    // COUPON (추가)
    );
}
```

### Database Schema

```sql
-- 사용자 쿠폰
CREATE TABLE user_coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    coupon_code VARCHAR(50) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    purchase_id BIGINT,
    INDEX idx_user_activity (user_id, campaign_activity_id)
);
```

### Funnel Mapping (COUPON)

| Funnel Step | COUPON Event | Description |
|-------------|--------------|-------------|
| **VISIT** | `PAGE_VIEW` | 쿠폰 페이지 방문 |
| **ENGAGE** | `CLAIM` | 쿠폰 받기 클릭 |
| **QUALIFY** | `ISSUED` | 쿠폰 발급 완료 |
| **PURCHASE** | `PURCHASE` | 쿠폰 사용 구매 |

---

## Summary: All Activity Types

### Complete Funnel Mapping

| Funnel Step | FCFS | RAFFLE | COUPON |
|-------------|------|--------|--------|
| **VISIT** | `PAGE_VIEW` | `PAGE_VIEW` | `PAGE_VIEW` |
| **ENGAGE** | `CLICK` | `APPLY` | `CLAIM` |
| **QUALIFY** | `APPROVED` | `WON` | `ISSUED` |
| **PURCHASE** | `PURCHASE` | `PURCHASE` | `PURCHASE` |

### Extension Points

현재 리팩토링으로 다음 확장 포인트가 준비되었습니다:

1. **FunnelStep enum**: 범용 4단계 정의 완료
2. **BehaviorEventService**:
   - `getEngageTriggerTypes()`: 새 이벤트 추가만 하면 됨
   - `getQualifyTriggerTypes()`: 새 이벤트 추가만 하면 됨
   - `getEventCountByTriggerTypes()`: 다중 이벤트 자동 집계
3. **DashboardService**: 변경 불필요 (이미 범용 퍼널 사용)

### Migration Checklist (새 활동 타입 추가 시)

- [ ] **1. Domain Events 정의** (core-service 또는 entry-service)
  - `RaffleAppliedEvent`, `RaffleWonEvent` 등
- [ ] **2. Controller 추가** (entry-service)
  - `RaffleController`, `CouponController`
- [ ] **3. Service Layer** (entry-service 또는 core-service)
  - 비즈니스 로직 (중복 체크, 재고 관리 등)
- [ ] **4. BackendEventPublisher 확장** (entry-service + core-service)
  - 도메인 이벤트 → Kafka 발행
- [ ] **5. BehaviorEventAdapter 확장** (entry-service + core-service)
  - 도메인 이벤트 → `UserBehaviorEventMessage` 변환
- [ ] **6. BehaviorEventService 업데이트** (core-service)
  - `getEngageTriggerTypes()`, `getQualifyTriggerTypes()`에 새 이벤트 추가
- [ ] **7. Database Schema** (core-service)
  - `raffle_applications`, `user_coupons` 등 테이블 추가
- [ ] **8. Test Scripts** (core-service/scripts)
  - `generate-raffle-funnel.sh` 생성

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend (Browser)                       │
│  - JS Tracker sends: PAGE_VIEW, CLICK, APPLY, CLAIM, etc   │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Entry-service (8081)                      │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ BehaviorEvent   │  │ Reservation  │  │ Raffle        │  │
│  │ Controller      │  │ Controller   │  │ Controller    │  │
│  │ (PAGE_VIEW)     │  │ (FCFS)       │  │ (APPLY)       │  │
│  └────────┬────────┘  └──────┬───────┘  └───────┬───────┘  │
│           │                  │                   │           │
│           ▼                  ▼                   ▼           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │        BackendEventPublisher                         │   │
│  │  - ReservationApprovedEvent → APPROVED              │   │
│  │  - RaffleAppliedEvent → APPLY                       │   │
│  └────────────────────────┬─────────────────────────────┘   │
└───────────────────────────┼─────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Kafka Topics  │
                    │ event.raw     │
                    └───────┬───────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
    ┌─────────────────┐         ┌─────────────────┐
    │ Kafka Connect   │         │  Core-service   │
    │ ES Sink         │         │  (8080)         │
    └────────┬────────┘         └────────┬────────┘
             │                           │
             ▼                           ▼
    ┌─────────────────┐         ┌─────────────────┐
    │ Elasticsearch   │         │  RaffleDraw     │
    │ behavior-events │         │  BatchJob       │
    │                 │         │  (WON event)    │
    └────────┬────────┘         └────────┬────────┘
             │                           │
             │                           ▼
             │                   ┌───────────────┐
             │                   │ Kafka Topics  │
             │                   │ event.raw     │
             │                   └───────┬───────┘
             │                           │
             └───────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Elasticsearch │
                    │ (VISIT, ENGAGE│
                    │ QUALIFY,      │
                    │ PURCHASE)     │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Dashboard API │
                    │ (4-stage      │
                    │  funnel)      │
                    └───────────────┘
```

---

## Testing Strategy

### Unit Tests
- `BehaviorEventServiceTest`: TriggerType 매핑 테스트
- `DashboardServiceTest`: 퍼널 집계 테스트

### Integration Tests
- RAFFLE 전체 플로우: Apply → Draw → Won → Purchase
- COUPON 전체 플로우: Claim → Issued → Purchase

### Test Scripts
```bash
# FCFS (기존)
./generate-full-funnel.sh 1 100

# RAFFLE (신규)
./generate-raffle-funnel.sh 2 200

# COUPON (신규)
./generate-coupon-funnel.sh 3 150
```

---

## Timeline Estimate

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| **Phase 1** | 현재 리팩토링 (완료) | ✅ 1 day |
| **Phase 2** | RAFFLE 추가 | 5-7 days |
| **Phase 3** | COUPON 추가 | 3-5 days |

**Total**: 약 2주 (RAFFLE + COUPON 둘 다 추가 시)

---

## References

- **Current Implementation**:
  - `FunnelStep.java`: Universal 4-stage enum
  - `BehaviorEventService.java`: Extensible trigger mapping
  - `DashboardService.java`: Activity-agnostic funnel

- **Elasticsearch Index**:
  - `behavior-events`: 모든 행동 이벤트 저장
  - Fields: `triggerType`, `pageUrl`, `userId`, `occurredAt`, `properties`

- **Kafka Topics**:
  - `axon.event.raw`: Frontend + Backend 이벤트 통합
