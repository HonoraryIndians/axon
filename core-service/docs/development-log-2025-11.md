# Axon CDP 개발 일지 (2025.11.14 ~ 11.26)

**기간**: 2025년 11월 14일 ~ 26일
**목표**: Activity-level 실시간 대시보드 백엔드 구축 및 성능 최적화
**상태**: ✅ 주요 목표 달성, 관측 가능성(Observability) 스택 구성 중

---

## ✅ 완료한 작업

### 1. 실시간 대시보드 시스템 (Dashboard API)

#### Dashboard API 구현
- **3개 데이터 소스 통합**: Elasticsearch (사용자 행동) + MySQL (승인/구매) + Redis (실시간 재고)
- **퍼널 분석**: VISIT → CLICK → APPROVED → PURCHASE
- **API**: `GET /api/v1/dashboard/activity/{id}`
- **LTV 및 코호트 분석**: `feat(dashboard): implement cohort analysis and ltv metrics` 완료

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

#### SSE 실시간 스트리밍
- **기술 선택**: SSE (WebSocket X)
- **갱신 주기**: 5초마다 자동 업데이트
- **리소스 관리**: onCompletion/onTimeout/onError 핸들러로 메모리 누수 방지

### 2. 성능 최적화 및 동시성 제어

#### Redisson 분산 락 (P0 - 긴급 해결됨)
**문제**: Redis `check-then-act`로 Race Condition 발생 → Over-booking
**해결**: `Redisson` 기반 분산 락 도입 완료 (`7617afa`)

```java
@DistributedLock(key = "#activityId")
public ReservationResult reserve(Long activityId, Long userId) {
    RLock lock = redissonClient.getLock("activity:" + activityId);
    if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
        try {
            // Atomic Operation
        } finally {
            lock.unlock();
        }
    }
}
```

#### Virtual Threads 도입 준비
**진행 상황**:
- `WebClient`를 `RestClient`로 리팩토링 완료 (`cf4d69d`)
- Virtual Threads 활성화를 위한 기반 마련

### 3. Backend Event Publishing

**문제**: 백엔드 이벤트(로그인, 구매) Kafka 발행 시 관심사 혼재
**해결**: Spring ApplicationEvents 패턴 도입, 트랜잭션 커밋 후 발행 보장

---

## 🚀 진행 중인 작업 (Observability)

### 관측 가능성 스택 (Observability Stack)
- **목표**: 시스템 메트릭 및 로그 중앙 집중화
- **구성 요소**:
  - **Elasticsearch**: 로그 및 데이터 저장
  - **Kibana**: 데이터 시각화
  - **Prometheus**: 메트릭 수집
  - **Grafana**: 메트릭 시각화 (대시보드)

### 클라우드 및 배포
- **진행 상황**: `deployment.yml` 수정 및 K8s 배포 파이프라인 구성
- **현재 상태**: 배포 완료, 인프라(Redis, Kafka 등) 구성 중

---

## 📅 타임라인 (업데이트)

```
✅ 2025-11-18 ~ 11-20
   └─ Dashboard API + SSE + Backend Events

✅ 2025-11-21 ~ 11-25
   └─ Redisson 분산 락 + LTV/Cohort 지표 구현 + RestClient 리팩토링

🔄 2025-11-26 ~ (현재)
   └─ Observability Stack (Prometheus/Grafana) 구성 + 배포 안정화

📅 2025-12월 1주차
   └─ 프론트엔드 연동 및 대시보드 UI 완성

📅 2025-12월 2주차
   └─ Virtual Threads 완전 전환 및 부하 테스트
```

---

## 💡 핵심 인사이트

### 기술 선택 원칙
- **SSE > WebSocket**: 단순함이 최고
- **Redisson**: 분산 환경에서의 정확성 보장 필수
- **Virtual Threads**: 기존 코드의 구조적 변경 없이 처리량 증대 가능성 확인

---

**작성**: 2025-11-26 (업데이트)
**작성자**: yangnail