# Payment & Resilience Flow Scenarios

> **문서 목적**: 결제 시스템의 정상 처리 및 장애 발생 시 복구 흐름을 시나리오별로 상세 도식화.

---

## 1. Scenario: Normal Payment (Happy Path)
사용자가 결제를 요청하고, 시스템이 정상적으로 DB에 저장하는 가장 이상적인 흐름입니다.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Frontend
    participant CoreAPI as PaymentController
    participant Service as PaymentService
    participant Redis
    participant DB as MySQL (Entry)

    User->>Frontend: Click "결제하기"
    Frontend->>CoreAPI: POST /process (Token)
    
    rect rgb(240, 255, 240)
        note right of CoreAPI: [Transaction Start]
        CoreAPI->>Service: processPayment(token, userId)
        
        Service->>Redis: GET RESERVATION_TOKEN:{token}
        Redis-->>Service: Payload (UserId, ActivityId...)
        
        Service->>Service: Validate (UserId Match?)
        
        Service->>DB: upsertEntry(APPROVED)
        DB-->>Service: Success
        
        Service->>Redis: DELETE RESERVATION_TOKEN:{token}
        Redis-->>Service: Deleted
        
        Service-->>CoreAPI: Success
        note right of CoreAPI: [Transaction Commit]
    end
    
    CoreAPI-->>Frontend: 200 OK
    Frontend-->>User: "결제 완료" 모달 표시
```

---

## 2. Scenario: Database Failure (Fail Path)
결제 검증은 통과했으나, DB 저장 시점(`upsertEntry`)에 예기치 못한 오류(Connection Timeout, Deadlock 등)가 발생한 상황입니다.
이때 **시스템은 에러를 반환하지만, 내부적으로는 '실패 로그'를 남겨두어야 합니다.**

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Frontend
    participant Service as PaymentService
    participant LogService as PaymentFailureLogService
    participant DB_Entry as MySQL (Entry Table)
    participant DB_Log as MySQL (Log Table)

    User->>Frontend: Click "결제하기"
    Frontend->>Service: POST /process
    
    rect rgb(255, 240, 240)
        note right of Service: [Main Transaction]
        Service->>Service: Redis Validate OK
        
        Service->>DB_Entry: upsertEntry()
        DB_Entry--xService: 💥 Exception (DB Error!)
        
        Service->>LogService: logFailure(payload, ex)
        
        rect rgb(255, 255, 224)
            note right of LogService: [New Transaction] (REQUIRES_NEW)
            LogService->>DB_Log: INSERT payment_failure_log (PENDING)
            DB_Log-->>LogService: Success
            note right of LogService: [Log Commit]
        end
        
        Service-->>Frontend: Success (Eventual Consistency)
        note right of Service: [Main Tx Rollback but Log Saved]
    end
    
    Frontend-->>User: "결제 완료" 모달 표시
```

---

## 3. Scenario: Auto Recovery (Recovery Path)
실패한 결제 건을 스케줄러가 감지하여 **Kafka를 통해 비동기로 재처리**하는 과정입니다. 사용자는 이미 에러를 봤지만, 시스템이 뒷단에서 데이터를 맞춰줍니다(Eventual Consistency).

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as PaymentRecoveryScheduler
    participant DB_Log as MySQL (Log Table)
    participant Kafka as Kafka (axon.payment.retry)
    participant Consumer as PaymentRetryConsumer
    participant Service as PaymentService
    participant DB_Entry as MySQL (Entry Table)

    %% 1. 스케줄러 실행 (1분 주기)
    loop Every 1 Minute
        Scheduler->>DB_Log: Find PENDING Logs (Top 10)
        DB_Log-->>Scheduler: [Log 1, Log 2...]
        
        rect rgb(240, 240, 255)
            note over Scheduler, Kafka: Kafka로 일감 던지기
            Scheduler->>Kafka: Send (Payload)
            Scheduler->>DB_Log: Update Status = RESOLVED
        end
    end

    %% 2. 컨슈머 처리 (비동기)
    Kafka->>Consumer: Consume Message
    Consumer->>Service: retryPayment(Payload)
    
    rect rgb(240, 255, 240)
        note right of Service: [Retry Logic]
        Service->>Service: ⚠️ Skip Token Validation
        Service->>DB_Entry: upsertEntry(APPROVED)
        DB_Entry-->>Service: Success
    end
    
    note over Service: ✅ 데이터 복구 완료
```

---

## 4. Scenario: Retry Failure (Recursive Fail)
재시도를 했는데도 또 실패하는 경우입니다. (예: DB 장애가 1분 이상 지속됨)
이 경우 다시 실패 로그를 쌓아 다음 스케줄러가 처리하게 합니다.

```mermaid
sequenceDiagram
    autonumber
    participant Consumer as PaymentRetryConsumer
    participant Service as PaymentService
    participant DB_Entry as MySQL (Entry Table)
    participant LogService as PaymentFailureLogService
    participant DB_Log as MySQL (Log Table)

    Consumer->>Service: retryPayment()
    Service->>DB_Entry: upsertEntry()
    DB_Entry--xService: 💥 Still Error! (DB 아직 안 살아남)
    
    Service--xConsumer: Throw Exception
    
    rect rgb(255, 230, 230)
        note right of Consumer: 재시도 실패 시 다시 로그 적재
        Consumer->>LogService: logFailure()
        LogService->>DB_Log: INSERT payment_failure_log (PENDING)
    end
    
    note over Consumer: 다음 스케줄러 타임에 다시 시도됨
```
