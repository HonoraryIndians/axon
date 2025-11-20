# Purchase 도메인 리팩토링 계획

> **팀 공유 문서** | 최종 수정일: 2025-01-19 | 담당자: @backend-team
> **현재 상태**: 계획 수립 완료, **구현 대기**

---

## 프로젝트 개요

### 배경
현재 `EventOccurrence` 엔티티는 원래 **모든 이벤트(구매, 클릭, 페이지뷰 등)를 저장하는 범용 테이블**로 설계되었습니다. 하지만 Elasticsearch가 행동 이벤트 로깅 책임을 가지면서 EventOccurrence의 역할이 크게 퇴색되었고, 현재는 **FCFS 캠페인 응모 성공 시 구매 정보만 저장하는 용도**로만 사용되고 있습니다.

이는 다음과 같은 문제를 야기합니다:
- **책임 불명확**: 테이블 이름이 실제 용도(구매 전용)를 반영하지 못함
- **확장성 제한**: 일반 쇼핑몰 구매 데이터를 저장하기에 부적합한 구조
- **통계 분석 어려움**: 캠페인 구매 vs 일반 구매 구분 불가능

### 목표
1. **EventOccurrence → Purchase 리팩토링**: 구매 전용 도메인으로 명확한 책임 분리
2. **구매 타입 구분**: FCFS 캠페인 구매 vs 일반 쇼핑몰 구매를 구분 가능하도록
3. **통계 대시보드 준비**: 향후 마케팅 대시보드에서 구매 패턴 분석 가능하도록 데이터 구조 개선
4. **가짜 데이터 생성**: 실제 쇼핑몰이 없는 상황에서 통계 테스트를 위한 realistic한 구매 이력 생성

### 비즈니스 가치
- **데이터 일관성**: 구매 데이터의 명확한 스키마로 분석 정확도 향상
- **마케팅 인사이트**: 캠페인 구매 vs 일반 구매 비교 분석으로 ROI 측정 가능
- **확장 가능성**: 향후 프로모션 효과 분석, 재구매율 추적 등 고급 분석 기반 마련

---

## 핵심 목표

### 1. 도메인 모델 명확화
**현재 문제**:
```java
// EventOccurrence - 이름만 봐서는 구매 데이터인지 알 수 없음
@Entity
@Table(name = "event_occurrences")
public class EventOccurrence {
    private Event event;  // Event와의 관계가 불필요
    private Map<String, Object> context;  // 구매 정보가 JSON에 숨어있음
}
```

**개선 후**:
```java
// Purchase - 도메인 의도가 명확함
@Entity
@Table(name = "purchases")
public class Purchase {
    private PurchaseType purchaseType;  // CAMPAIGN or NORMAL
    private Long campaignActivityId;     // 명시적 필드
    private BigDecimal price;            // 타입 안전성
    private Integer quantity;
}
```

**얻는 이점**:
- ✅ **가독성**: 코드 리뷰 시 즉시 용도 파악 가능
- ✅ **타입 안전성**: JSON Map 대신 강타입 필드로 컴파일 타임 검증
- ✅ **쿼리 성능**: 인덱스 최적화 가능 (JSON 필드는 인덱싱 어려움)

---

### 2. 구매 타입 구분으로 비즈니스 인사이트 확보

**현재 한계**:
- FCFS 캠페인 구매인지 일반 구매인지 구분 불가능
- "캠페인이 실제로 매출에 기여했는가?" 질문에 답할 수 없음

**개선 후**:
```java
public enum PurchaseType {
    CAMPAIGN,  // FCFS 캠페인을 통한 구매
    NORMAL     // 일반 쇼핑몰 구매
}
```

**가능한 분석**:
| 분석 항목 | 쿼리 예시 | 비즈니스 가치 |
|----------|----------|-------------|
| 캠페인 구매율 | `SELECT COUNT(*) FROM purchases WHERE purchase_type = 'CAMPAIGN'` | 캠페인 효과 측정 |
| 프로모션 vs 일반 매출 비교 | `SELECT purchase_type, SUM(price * quantity) GROUP BY purchase_type` | ROI 계산 |
| 캠페인 후 재구매율 | `SELECT userId FROM purchases WHERE purchase_type = 'CAMPAIGN' AND userId IN (SELECT userId FROM purchases WHERE purchase_type = 'NORMAL')` | 고객 전환율 추적 |

---

### 3. 통계 대시보드 데이터 기반 마련

**현재 마케팅 대시보드 상태** (`marketing-dashboard-development-plan.md` 참고):
- Phase 1, 2 완료: 백엔드 API 구현 완료
- Phase 3 진행 예정: 프론트엔드 시각화

**Purchase 리팩토링이 대시보드에 미치는 영향**:
```
마케팅 대시보드 요구사항:
├── 퍼널 분석: 방문 → 참여 → 승인 → 구매
│   └── 구매 데이터: Elasticsearch (triggerType=PURCHASE)
├── 프로모션 효과 분석: 프로모션 vs 일반 구매량 비교
│   └── ❌ 현재: 구매 타입 구분 불가능
│   └── ✅ 개선 후: purchase_type 필드로 쉽게 집계
└── 실시간 모니터링: 재고, 참여자 수
```

**통합 쿼리 예시**:
```sql
-- 프로모션 효과 분석 (리팩토링 후 가능)
SELECT
    DATE(purchased_at) AS date,
    purchase_type,
    COUNT(*) AS count,
    SUM(price * quantity) AS revenue
FROM purchases
WHERE purchased_at BETWEEN '2025-01-01' AND '2025-01-31'
GROUP BY DATE(purchased_at), purchase_type
ORDER BY date;
```

---

### 4. 가짜 데이터 생성으로 테스트 환경 구축

**문제 상황**:
- 실제 쇼핑몰이 아직 없어 일반 구매 데이터가 0건
- 통계 대시보드 개발 시 실데이터 없이 테스트 불가능
- 캠페인 효과 비교 분석을 검증할 방법 없음

**해결 방안**: Java Faker 라이브러리로 realistic한 가짜 데이터 생성
```java
// FakePurchaseDataGenerator
- 70% 일반 구매, 30% 캠페인 구매 (실제 비율 시뮬레이션)
- 1,000원~100,000원 랜덤 가격 (정규분포 적용 가능)
- 과거 1년간 날짜 랜덤 분포
- userId, productId는 기존 데이터와 정합성 유지
```

**테스트 시나리오 예시**:
1. **시나리오 A**: 1,000개 구매 데이터 생성 → 대시보드 API 응답 시간 측정
2. **시나리오 B**: 특정 사용자(userId=1)의 구매 이력 20개 생성 → 사용자별 통계 검증
3. **시나리오 C**: 캠페인 기간(2025-01-01~01-31) 집중 데이터 생성 → 프로모션 효과 차트 테스트

---

## 아키텍처 개선 사항

### 현재 데이터 플로우
```
Entry-service (FCFS 응모 성공)
    │ Kafka: axon.campaign-activity.command
    ▼
Core-service (CampaignActivityConsumerService)
    │
    ▼
CampaignActivityEntryService.upsertEntry()
    │ IF status == APPROVED
    ▼
CampaignActivityApprovedEvent (내부 이벤트)
    │
    ▼
PurchaseEventHandler
    ├── ProductService.decreaseStock()
    ├── UserSummaryService.recordPurchase()
    └── EventOccurrenceService.process("Purchase", request)
         │
         ▼
    PurchaseTriggerStrategy.createEventOccurrence()
         │
         ▼
    EventOccurrenceRepository.save(eventOccurrence)
         │
         ▼
    [저장] event_occurrences 테이블
```

### 개선 후 데이터 플로우
```
Entry-service (FCFS 응모 성공)
    │ Kafka: axon.campaign-activity.command
    ▼
Core-service (CampaignActivityConsumerService)
    │
    ▼
CampaignActivityEntryService.upsertEntry()
    │ IF status == APPROVED
    ▼
CampaignActivityApprovedEvent (내부 이벤트)
    │
    ▼
PurchaseEventHandler
    ├── ProductService.decreaseStock()
    ├── UserSummaryService.recordPurchase()
    └── PurchaseService.createPurchase()  ← 단순화!
         │
         ▼
    PurchaseRepository.save(purchase)
         │
         ▼
    [저장] purchases 테이블
```

**개선 효과**:
- ✅ **계층 감소**: EventOccurrenceService, Strategy 패턴 제거로 호출 깊이 2단계 감소
- ✅ **유지보수성**: 중간 추상화 제거로 디버깅 용이
- ✅ **성능**: 불필요한 Event 엔티티 조회 제거

---

## 데이터 모델 비교

### Before: EventOccurrence
```sql
CREATE TABLE event_occurrences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,           -- Event 테이블 FK (불필요)
    occurred_at DATETIME NOT NULL,
    user_id BIGINT NULL,
    page_url VARCHAR(2048) NULL,        -- 구매에는 무의미
    event_context TEXT NOT NULL,        -- JSON: {campaignActivityId, productId}
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id)
);
```

**문제점**:
- ❌ `event_id`: Event 엔티티 조회 오버헤드
- ❌ `page_url`: 구매 컨텍스트와 무관
- ❌ `event_context`: JSON 파싱 오버헤드, 인덱싱 불가

---

### After: Purchase
```sql
CREATE TABLE purchases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    campaign_activity_id BIGINT NULL,    -- CAMPAIGN 타입일 때만 사용
    purchase_type VARCHAR(20) NOT NULL,  -- ENUM: CAMPAIGN, NORMAL
    price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    purchased_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_purchase_type (purchase_type),
    INDEX idx_purchased_at (purchased_at)
);
```

**개선점**:
- ✅ **명시적 필드**: price, quantity가 타입 안전하게 저장
- ✅ **쿼리 최적화**: 인덱스로 집계 쿼리 성능 향상
- ✅ **데이터 무결성**: CAMPAIGN 타입일 때 campaignActivityId 필수 검증

---

## 기술 스택 및 구현 방식

### JPA DDL Auto 방식 채택 이유
```yaml
# application.yml
spring.jpa.hibernate.ddl-auto: create
```

**장점**:
- ✅ **빠른 프로토타이핑**: 엔티티만 수정하면 테이블 자동 생성
- ✅ **개발 환경 적합**: 매번 깨끗한 상태로 시작 가능
- ✅ **마이그레이션 스크립트 불필요**: Flyway/Liquibase 설정 생략

**주의사항**:
- ⚠️ 운영 환경에서는 `validate`로 변경 필수
- ⚠️ 애플리케이션 재시작 시 모든 데이터 삭제

---

### Java Faker 라이브러리 선택 이유
```gradle
implementation 'net.datafaker:datafaker:2.1.0'
```

**비교 분석**:
| 방식 | 장점 | 단점 | 선택 여부 |
|------|------|------|----------|
| **Java Faker** | 실제같은 데이터 (이름, 주소, 날짜 등), 다양한 로케일 지원 | 의존성 추가 | ✅ 채택 |
| 직접 구현 (Random) | 의존성 없음, 빠름 | 데이터 품질 낮음, 반복적 코드 | ❌ 기각 |
| SQL 스크립트 | 간단함 | 유연성 낮음, 유지보수 어려움 | ❌ 기각 |
| Spring Batch + Faker | 대규모 데이터 생성 최적화 | 과도한 복잡도 (현재 불필요) | ⏸️ 향후 고려 |

**실제 사용 예시**:
```java
Faker faker = new Faker(new Locale("ko"));
faker.number().numberBetween(1000, 100000);  // 가격
faker.number().numberBetween(1, 365);        // 과거 일수
```

---

## 성공 지표 (KPI)

### 코드 품질
- [ ] 순환 복잡도 감소: EventOccurrence 관련 클래스 7개 → Purchase 관련 3개
- [ ] 테스트 커버리지: Purchase 도메인 80% 이상
- [ ] 컴파일 에러: 0건 (리팩토링 완료 후)

### 성능
- [ ] Purchase 저장 시간: < 50ms (EventOccurrence 대비 20% 향상 목표)
- [ ] 대시보드 집계 쿼리: < 200ms (인덱스 활용)
- [ ] 가짜 데이터 생성: 1,000건/초 이상

### 비즈니스
- [ ] 통계 대시보드 Phase 3 개발 가능 (프로모션 효과 분석 위젯 추가)
- [ ] 마케팅 팀 요청 쿼리 응답 시간 < 5초

---

## 단계별 구현 계획

### Phase 1: 도메인 모델 생성 (예상 소요: 1일)
**목표**: Purchase 엔티티 및 PurchaseType Enum 생성

**Task**:
- [x] `domain/purchase/PurchaseType.java` 생성 ✅
- [x] `domain/purchase/Purchase.java` 생성 ✅
- [x] Builder 패턴 구현 ✅
- [x] 유효성 검증 로직 (CAMPAIGNACTIVITY 타입 시 campaignActivityId 필수) ✅

**검증 방법**:
- [x] 애플리케이션 재시작 → `purchases` 테이블 생성 확인 ✅
- [x] MySQL에서 `DESC purchases;` 실행 → 컬럼 구조 확인 ✅

---

### Phase 2: 리포지토리 및 서비스 레이어 (예상 소요: 1일)
**목표**: PurchaseRepository, PurchaseService 구현

**Task**:
- [x] `repository/PurchaseRepository.java` 생성 ✅
- [x] 커스텀 쿼리 메소드 정의 (findByUserId, findByPurchaseType 등) ✅
- [x] `service/PurchaseService.java` 생성 ✅
- [x] createPurchase() 메소드 구현 ✅

**검증 방법**:
- [x] 단위 테스트 작성 및 실행 ✅
- [x] Mockito로 Repository 호출 검증 ✅

---

### Phase 3: 기존 코드 통합 (예상 소요: 0.5일)
**목표**: PurchaseEventHandler 수정 및 EventOccurrence 제거

**Task**:
- [x] `PurchaseHandler.java` 생성 (PurchaseInfoDto 이벤트 핸들러) ✅
- [x] EventOccurrence 관련 파일 삭제 (8개) ✅
  - EventOccurrence.java, EventOccurrenceRepository.java
  - EventOccurrenceRequest/Response.java
  - EventOccurrenceService.java, EventOccurrenceStrategy.java
  - PurchaseTriggerStrategy.java
  - 기존 PurchaseEventHandler.java (PurchaseHandler로 대체됨)
- [x] BatchConfig.java 쿼리 수정 (EventOccurrence → Purchase) ✅
- [x] CampaignMetricsService.java 의존성 제거 ✅
- [x] EventCollectorController.java import 정리 ✅

**검증 방법**:
- [x] Entry-service에서 FCFS 응모 요청 ✅
- [x] Kafka 메시지 전송 확인 ✅
- [x] purchases 테이블에 데이터 저장 확인 ✅

---

### Phase 4: 가짜 데이터 생성기 구현 (예상 소요: 1일) ← **현재 단계**
**목표**: FakePurchaseDataGenerator 구현 및 테스트

**Task**:
- [ ] `build.gradle`에 Faker 의존성 추가
- [ ] `util/FakePurchaseDataGenerator.java` 생성
- [ ] generateFakePurchases() 메소드 구현
- [ ] Admin API 또는 CommandLineRunner 선택 구현

**검증 방법**:
- [ ] 1,000개 데이터 생성 실행
- [ ] MySQL에서 purchase_type 비율 확인 (NORMAL 70%, CAMPAIGN 30%)
- [ ] 가격, 날짜 분포 확인

---

### Phase 5: 테스트 및 문서화 (예상 소요: 1일)
**목표**: 통합 테스트 및 README 업데이트

**Task**:
- [ ] PurchaseRepositoryTest 작성
- [ ] PurchaseServiceTest 작성
- [ ] PurchaseEventHandlerTest 작성
- [ ] End-to-End 테스트 (FCFS 응모 → Purchase 저장)
- [ ] `README.md` 업데이트 (Purchase 기능 설명)

**검증 방법**:
- [ ] 모든 테스트 통과
- [ ] 코드 리뷰 승인

---

## 아키텍처 결정사항 (ADR)

### 1. EventOccurrence 완전 삭제 vs 공존

**결정**: EventOccurrence는 **완전 삭제**하고 Purchase로 대체

**배경**:
- EventOccurrence의 원래 목적: 범용 이벤트 저장
- 현재 용도: 구매 이벤트만 저장 (다른 이벤트는 Elasticsearch 사용)
- Event 엔티티와의 관계가 불필요한 오버헤드 발생

**장점**:
- ✅ 코드베이스 단순화 (7개 파일 삭제)
- ✅ 유지보수 포인트 감소
- ✅ 신규 개발자 온보딩 용이

**트레이드오프**:
- ⚠️ 기존 EventOccurrence 데이터 삭제 (개발 환경이므로 문제없음)
- ⚠️ Event 엔티티는 유지 (향후 다른 이벤트 트래킹 필요 시)

---

### 2. 가짜 데이터 생성 방법

**결정**: **Admin REST API 방식** 채택

**비교**:
| 방식 | 장점 | 단점 |
|------|------|------|
| **Admin API** | 운영 중에도 호출 가능, 파라미터 조정 용이 | 보안 설정 필요 |
| CommandLineRunner | 간단함 | 재실행 시 애플리케이션 재시작 필요 |
| 테스트 코드 | 개발 환경에만 존재 | 운영 환경에서 사용 불가 |

**선택 이유**:
- ✅ 대시보드 개발 중 반복적으로 데이터 생성 필요
- ✅ count, userId 등 파라미터 동적 조정 가능
- ✅ 운영 환경에서도 초기 데이터 로딩 가능

**보안 조치**:
```java
@RestController
@RequestMapping("/admin/fake-data")
@PreAuthorize("hasRole('ADMIN')")  // 추후 추가
public class FakeDataController { ... }
```

---

### 3. Purchase와 Product 간 관계 설정

**결정**: **Foreign Key 없이 productId만 저장**

**배경**:
- Purchase는 이력 데이터 (상품 삭제 후에도 구매 이력 유지 필요)
- Product 엔티티 변경 시 Purchase 영향 최소화

**장점**:
- ✅ 느슨한 결합 (Product 삭제 시에도 Purchase 유지)
- ✅ 성능 향상 (JOIN 없이 집계 쿼리 가능)

**트레이드오프**:
- ⚠️ 참조 무결성 검증 불가 (애플리케이션 레벨에서 검증)

---

## 📚 참고 문서 및 학습 자료

### 내부 문서
- [마케팅 대시보드 개발계획](./marketing-dashboard-development-plan.md) - Purchase 데이터 활용 계획
- [CLAUDE.md 개발 가이드](../CLAUDE.md) - 프로젝트 전체 아키텍처
- [구매 이벤트 플로우](./purchase-event-flow.md) - 현재 구매 플로우 상세

### 외부 자료
- [JPA Entity 설계 가이드](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Java Faker 라이브러리](https://github.com/datafaker-net/datafaker)
- [Domain-Driven Design: Entities vs Value Objects](https://martinfowler.com/bliki/EvansClassification.html)

---

## 향후 확장 계획

### Phase A: 배송 정보 추가 (우선순위: 낮음)
- 배송 주소, 송장번호 필드 추가
- 가짜 데이터에 Faker 주소 생성 포함
- 배송 상태 추적 기능

### Phase B: 주문 도메인 분리 (우선순위: 중간)
- Order 엔티티 생성 (1 Order : N Purchases)
- 주문번호, 결제 방법, 총액 등 관리
- 재고 관리 시스템 통합

### Phase C: 구매 분석 대시보드 위젯 (우선순위: 높음)
**연계 문서**: `marketing-dashboard-development-plan.md` Phase 3
- **프로모션 효과 분석 위젯**:
  - SQL: `SELECT purchase_type, COUNT(*), SUM(price) FROM purchases GROUP BY purchase_type`
  - 시각화: Dual Bar Chart (Chart.js)
- **재구매율 위젯**:
  - SQL: 사용자별 구매 횟수 집계
  - 시각화: Funnel Chart

---

## 문서 업데이트 로그

| 날짜 | 변경사항 | 작성자 |
|------|----------|--------|
| 2025-01-19 | 초기 리팩토링 계획 작성 | @backend-team |
| 2025-01-19 | ADR 섹션 추가 (EventOccurrence 삭제 결정) | @backend-team |

---

**💡 이 문서는 팀 공유용이므로, 진행상황 업데이트 시 체크박스를 체크하고 커밋해주세요!**
