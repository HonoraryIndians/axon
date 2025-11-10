# Purchase Event Flow & Instrumentation Guide

## 1. 동작 개요

### 엔드 투 엔드 플로우
1. **Kafka 메시지 수신**  
   `entry-service`가 선착순 캠페인 요청을 Kafka(`axon.campaign-activity.command`)에 적재하면 `FirstComeFirstServeStrategy` → `CampaignActivityEntryService.upsertEntry()`가 실행되고, 엔트리를 `APPROVED` 상태로 저장합니다.
2. **도메인 이벤트 발행**  
   `CampaignActivityEntryService`는 엔트리를 저장한 뒤 `CampaignActivityApprovedEvent`를 발행합니다. 서비스 자체는 엔트리 저장만 담당하고, 후속 처리는 이벤트 핸들러에 위임합니다.
3. **PurchaseEventHandler 실행**  
   `PurchaseEventHandler`가 같은 트랜잭션 안에서 실행되며 다음을 수행합니다.  
   - `productService.decreaseStock(...)` : 재고 감소  
   - `userSummaryService.recordPurchase(...)` : `user_summary.last_purchase_at` 갱신  
   - `eventOccurrenceService.process("Purchase", occurrenceRequest)` : 구매 이벤트 로그(Event Occurrence) 기록
4. **EventOccurrenceStrategy**  
   `EventOccurrenceService`는 `"Purchase"` 트리거에 해당하는 전략(`PurchaseTriggerStrategy`)을 찾아 Event 정의(`events`)를 확인하고, 발생 로그(`event_occurrences`)를 저장합니다.  
   - `eventId`가 명시된 경우 해당 이벤트 사용  
   - `eventId`가 없으면 활성화된 Purchase 트리거 이벤트를 찾아 기본값으로 사용

### 요약
| 단계 | 담당 | 설명 |
| --- | --- | --- |
| 1 | Entry Service | Kafka로 선착순 요청 발행 |
| 2 | Core Service | 엔트리 저장 후 `CampaignActivityApprovedEvent` 발행 |
| 3 | PurchaseEventHandler | 재고 감소, UserSummary 갱신, EventOccurrence 기록 |
| 4 | PurchaseTriggerStrategy | 이벤트 정의 확인 및 EventOccurrence 저장 |

## 2. Event vs Event Occurrence
| 구분 | 설명 |
| --- | --- |
| **Event** | 마케터/관리자가 정의한 “트리거 조건” (예: 특정 URL 진입 시 로그 남기기). `events` 테이블에 저장됩니다. |
| **Event Occurrence** | 위 조건이 실제로 발생했을 때 남기는 로그입니다 (`event_occurrences`). 구매 확정, 선착순 승인 같은 비즈니스 이벤트도 이 흐름으로 기록합니다. |

### 활용 구분
- **Event Occurrence(DB)** : 구매/선착순 승인 등 비즈니스 로직에 바로 활용되는 이벤트만 저장.
- **Fluentd/ES 등 로그 파이프라인** : 단순 페이지 뷰, 클릭 등 대량 행동 로그는 프런트 instrumentation이 감지해 로그 파이프라인으로 흘려보냅니다.

## 3. 프런트 Instrumentation 계획
1. **Event 구성 전달**  
   백엔드가 특정 사용자에게 적용되는 Event 정의(트리거 타입, URL, 추가 조건 등)를 JS 코드 스니펫에 내려줍니다.
2. **사용자 행동 감지**  
   JS 스니펫이 URL 변경/라우팅 이벤트를 감지해 Event 설정과 매칭합니다.
3. **Event Occurrence 전송**  
   조건에 맞으면 엔트리 서비스(또는 Kafka/Fluentd)에 `EventOccurrenceRequest` 형태로 전송합니다.
4. **코어 서비스 기록**  
   `EventOccurrenceService`가 전략을 통해 Event Occurrence를 DB에 저장합니다.

> URL 진입과 같이 대량으로 발생하는 로그는 Fluentd/ES 쪽으로만 전송하고, 구매/선착순처럼 비즈니스 로직이 필요한 이벤트는 DB에도 저장하는 구조를 유지합니다.

## 4. 주요 클래스 & 책임
| 클래스 | 역할 |
| --- | --- |
| `CampaignActivityEntryService` | 엔트리 저장, `CampaignActivityApprovedEvent` 발행 |
| `CampaignActivityApprovedEvent` | 구매/선착순 승인 도메인 이벤트 |
| `PurchaseEventHandler` | 재고 차감, 사용자 요약 갱신, Event Occurrence 기록 |
| `EventOccurrenceService` | 트리거 타입에 맞는 전략 실행 |
| `PurchaseTriggerStrategy` | Purchase 이벤트 정의 조회 후 Event Occurrence 저장 |
| `UserSummaryService` | `user_summary`의 `last_purchase_at`, `last_login_at` 갱신 |

## 5. 테스트 및 데이터 시드
- **테스트**  
  - `CampaignActivityConsumerServiceTest` : Kafka 메시지 처리 → 재고 감소 & UserSummary 갱신 통합 확인  
  - `UserSummaryServiceTest` : `recordPurchase`/`recordLogin` 단위 테스트
- **데이터 시드**  
  - `data.sql` 또는 `CommandLineRunner`를 사용해 상품, 캠페인, 활동, Purchase 트리거 이벤트를 기본값으로 등록  
  - 예시: `INSERT INTO events (...) VALUES (... 'PURCHASE', 'ACTIVE', ...)` 등

## 6. 향후 작업 & TODO
- [ ] JS 스니펫에서 이벤트 목록을 받아 URL 진입/클릭을 감지하고, 조건에 맞으면 엔트리 서비스에 이벤트 전송
- [ ] Purchase 외에도 필요한 트리거 타입(예: LOGIN_SUCCESS, SIGNUP 등)별 전략 구현
- [ ] 이벤트 정의/전달 API 추가 : 특정 사용자에게 활성화된 Event 목록을 내려주는 API
- [ ] Fluentd/ES 파이프라인 구축 후 단순 행동 로그를 로그 인프라로 분리
- [ ] Event 정의 UI/관리 도구 업데이트 (트리거 타입, URL, 추가 조건 등)
