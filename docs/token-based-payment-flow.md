# 토큰 기반 결제 플로우 설계 문서

## 목차
1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [토큰 시스템](#토큰-시스템)
4. [플로우 상세](#플로우-상세)
5. [보안 설계](#보안-설계)
6. [예외 처리](#예외-처리)
7. [성능 최적화](#성능-최적화)

---

## 개요

### 목적
FCFS(First Come First Serve) 선착순 캠페인에서 결제 프로세스 중 데이터 무결성과 보안을 보장하기 위한 2단계 토큰 시스템

### 핵심 요구사항
- **시간 제한**: 선착순 통과 후 5분 내 결제 진입 필수
- **데이터 신뢰성**: 결제 중 1차 토큰 만료 시에도 데이터 유지
- **재결제 지원**: 브라우저 종료 후 재접속 시 기존 토큰 재사용
- **보안**: 토큰 탈취 및 위변조 방지

### 설계 원칙
1. **1차 토큰**: 시간 제한 책임 (TTL 5분 고정)
2. **2차 토큰**: 데이터 신뢰성 책임 (TTL 30분, 갱신 가능)
3. **결정론적 생성**: 재결제 시 동일 토큰 재사용
4. **Redis 기반**: DB 접근 없이 빠른 검증

---

## 아키텍처

### 전체 구조

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │
       │ 1. POST /api/v1/entries
       │
┌──────▼──────────────────────────────────────────┐
│            EntryController                      │
│  ┌──────────────────────────────────────────┐  │
│  │ 1. FCFS 검증 (Redis Set, Counter)       │  │
│  │ 2. 1차 토큰 존재 확인 (재결제 시)       │  │
│  │ 3. 빠른/무거운 검증 (조건부)            │  │
│  │ 4. 1차 토큰 발급                         │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
       │
       │ Response: { "reservationToken": "MTox..." }
       │
┌──────▼──────────────────────────────────────────┐
│         PaymentController                       │
│  ┌──────────────────────────────────────────┐  │
│  │ /prepare                                  │  │
│  │  - 1차 토큰 검증                         │  │
│  │  - 사용자 소유권 확인                    │  │
│  │  - 2차 토큰 발급 (또는 TTL 갱신)        │  │
│  │                                           │  │
│  │ /confirm                                  │  │
│  │  - 2차 토큰 검증                         │  │
│  │  - 사용자 소유권 확인                    │  │
│  │  - Kafka 전송 (3회 재시도)               │  │
│  │  - 토큰 정리                              │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
       │
       │ Kafka
       │
┌──────▼──────────────────────────────────────────┐
│          Core-service (MySQL)                   │
└─────────────────────────────────────────────────┘
```

---

## 토큰 시스템

### 1차 토큰 (Reservation Token)

#### 역할
- **선착순 통과 증명**: FCFS 검증을 통과한 사용자에게 발급
- **시간 제한 적용**: 5분 내 결제 진입 강제
- **결제 권한 부여**: `/prepare` 엔드포인트 접근 권한

#### 구조
```
Payload: "userId:campaignActivityId"
예: "1:100"

HMAC-SHA256 서명:
signature = HMAC(SECRET_KEY, "1:100")
예: "a7f3b9c2d1e4f5a6b7c8d9e0f1a2b3c4..."

최종 토큰:
Base64("1:100:a7f3b9c2...")
= "MToxMDA6YTdmM2I5YzJkMWU0..."
```

#### 특징
- **결정론적 생성**: 같은 userId + campaignActivityId → 같은 토큰
- **HMAC 서명**: SECRET_KEY 없이는 생성 불가 (위변조 방지)
- **TTL 고정**: 5분, 갱신 불가
- **Redis 저장**: `RESERVATION_TOKEN:{token}` → `{payload}`

#### 생성 코드
```java
public String generateDeterministicToken(Long userId, Long campaignActivityId) {
    String payload = userId + ":" + campaignActivityId;

    // HMAC-SHA256 서명 (ThreadLocal 재사용)
    String signature = hmacSha256Hex(payload);

    String combined = payload + ":" + signature;

    // Base64 URL-Safe 인코딩
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(combined.getBytes(StandardCharsets.UTF_8));
}
```

---

### 2차 토큰 (Approval Token)

#### 역할
- **데이터 백업**: 1차 토큰 만료 시 결제 데이터 보존
- **결제 완료 권한**: `/confirm` 엔드포인트 접근 권한
- **재결제 지원**: TTL 갱신으로 여러 번 결제 시도 가능

#### 구조
```
Token Key: "userId:campaignActivityId"
예: "1:100"

Redis Key: "PAYMENT_APPROVED_TOKEN:1:100"

Value (Payload):
{
  "userId": 1,
  "campaignActivityId": 100,
  "productId": 200,
  "campaignActivityType": "FIRST_COME_FIRST_SERVE",
  "reservationToken": "MToxMDA6YTdm..."
}
```

#### 특징
- **단순 키**: userId:campaignActivityId 조합
- **TTL 갱신 가능**: `/prepare` 재호출 시 30분 리셋
- **Redis 저장**: `PAYMENT_APPROVED_TOKEN:{userId}:{campaignActivityId}` → `{payload}`
- **1차 토큰 참조**: 원본 1차 토큰 정보 포함

#### 생성 코드
```java
public String CreateApprovalToken(PaymentApprovalPayload payload) {
    String redisKey = payload.getUserId() + ":" + payload.getCampaignActivityId();
    String fullRedisKey = APPROVAL_PREFIX + redisKey;

    redisTemplate.opsForValue().set(
        fullRedisKey,
        payload,
        APPROVALTOKEN_TTL_MINUTES,  // 30분
        TimeUnit.MINUTES
    );

    return redisKey;  // "1:100"
}
```

---

## 플로우 상세

### 1. 정상 플로우 (최초 응모)

```
사용자 액션                    서버 처리                         Redis 상태
─────────────────────────────────────────────────────────────────────────────
1. 응모 버튼 클릭
                    ┌─────────────────────────────────┐
                    │ POST /api/v1/entries            │
                    │                                 │
                    │ 1. 1차 토큰 생성 (결정론적)    │
                    │    token = "MToxMDA6YTdm..."    │
                    │                                 │
                    │ 2. Redis 조회                   │
                    │    → 없음 (최초 응모)          │
                    │                                 │
                    │ 3. FCFS 검증                    │
                    │    - Redis Set 추가             │  ┌─────────────────┐
                    │    - Counter 증가               │  │ campaignActivity│
                    │    → 통과                       │  │ :100:participants│
                    │                                 │  │  └─ userId=1    │
                    │ 4. 빠른/무거운 검증             │  └─────────────────┘
                    │    → 통과                       │
                    │                                 │
                    │ 5. 1차 토큰 Redis 저장          │  ┌─────────────────┐
                    │    TTL 5분                      │  │ RESERVATION_    │
                    │                                 │  │ TOKEN:MToxMDA...│
                    └─────────────────────────────────┘  │  └─ {payload}   │
                                                          │  TTL: 300초     │
  Response:                                               └─────────────────┘
  { "reservationToken": "MToxMDA6YTdm..." }

─────────────────────────────────────────────────────────────────────────────

2. 결제 페이지 이동
   (사용자가 결제 정보 입력 중)

─────────────────────────────────────────────────────────────────────────────

3. 결제 진행 버튼 클릭
                    ┌─────────────────────────────────┐
                    │ POST /api/v1/payments/prepare   │
                    │                                 │
                    │ 1. 1차 토큰 검증                │
                    │    Redis 조회 → 존재           │
                    │                                 │
                    │ 2. 사용자 소유권 확인           │
                    │    payload.userId == 1 ✓       │
                    │                                 │
                    │ 3. 2차 토큰 존재 확인           │
                    │    → 없음 (최초)               │
                    │                                 │
                    │ 4. 2차 토큰 발급                │  ┌─────────────────┐
                    │    key = "1:100"                │  │ PAYMENT_APPROVED│
                    │    TTL 30분                     │  │ _TOKEN:1:100    │
                    └─────────────────────────────────┘  │  └─ {payload}   │
                                                          │  TTL: 1800초    │
  Response:                                               └─────────────────┘
  { "success": true, "approvalToken": "1:100" }

─────────────────────────────────────────────────────────────────────────────

4. PG사 결제 완료
                    ┌─────────────────────────────────┐
                    │ POST /api/v1/payments/confirm   │
                    │                                 │
                    │ 1. 2차 토큰 검증                │
                    │    Redis 조회 → 존재           │
                    │                                 │
                    │ 2. 사용자 소유권 확인           │
                    │    payload.userId == 1 ✓       │
                    │                                 │
                    │ 3. Kafka 전송 (3회 재시도)     │
                    │    → 성공                       │
                    │                                 │
                    │ 4. 토큰 정리                    │  ┌─────────────────┐
                    │    - 1차 토큰 삭제              │  │ (모두 삭제됨)   │
                    │    - 2차 토큰 삭제              │  └─────────────────┘
                    └─────────────────────────────────┘

  Response:
  { "reservationResult": { "status": "SUCCESS" } }

─────────────────────────────────────────────────────────────────────────────
```

---

### 2. 재결제 플로우 (브라우저 종료 후 재접속)

```
사용자 액션                    서버 처리                         Redis 상태
─────────────────────────────────────────────────────────────────────────────
상황: 이전에 응모 후 결제 중 브라우저 종료 (3분 경과)

1. 재응모 버튼 클릭
                    ┌─────────────────────────────────┐  ┌─────────────────┐
                    │ POST /api/v1/entries            │  │ RESERVATION_    │
                    │                                 │  │ TOKEN:MToxMDA...│
                    │ 1. 1차 토큰 생성 (결정론적)    │  │  └─ {payload}   │
                    │    token = "MToxMDA6YTdm..."    │  │  TTL: 120초 남음│
                    │    (이전과 동일한 토큰!)       │  └─────────────────┘
                    │                                 │
                    │ 2. Redis 조회                   │
                    │    → 존재! ✅                  │
                    │                                 │
                    │ 3. 검증 스킵 ✅                │
                    │    - FCFS 검증 안 함           │
                    │    - 빠른 검증 안 함           │
                    │    - 무거운 검증 안 함         │
                    │                                 │
                    │ 4. 기존 토큰 그대로 반환        │
                    │    (TTL 갱신 안 함, 2분 남음)  │
                    └─────────────────────────────────┘

  Response:
  { "reservationToken": "MToxMDA6YTdm..." }  ← 같은 토큰!

─────────────────────────────────────────────────────────────────────────────

2. 결제 진행 버튼 클릭
                    ┌─────────────────────────────────┐  ┌─────────────────┐
                    │ POST /api/v1/payments/prepare   │  │ PAYMENT_APPROVED│
                    │                                 │  │ _TOKEN:1:100    │
                    │ 1. 1차 토큰 검증                │  │  └─ {payload}   │
                    │    Redis 조회 → 존재 (2분 남음)│  │  TTL: 1500초    │
                    │                                 │  │  (이전 발급)    │
                    │ 2. 사용자 소유권 확인 ✓        │  └─────────────────┘
                    │                                 │
                    │ 3. 2차 토큰 존재 확인           │
                    │    → 있음! (이전에 발급됨)     │
                    │                                 │  ┌─────────────────┐
                    │ 4. 2차 토큰 TTL 갱신 ✅        │  │ PAYMENT_APPROVED│
                    │    30분 리셋                    │  │ _TOKEN:1:100    │
                    └─────────────────────────────────┘  │  └─ {payload}   │
                                                          │  TTL: 1800초    │
  Response:                                               │  (리셋됨!)      │
  { "success": true, "approvalToken": "1:100" }           └─────────────────┘

─────────────────────────────────────────────────────────────────────────────

3. PG사 결제 완료
   (이후 정상 플로우와 동일)
```

---

### 3. 1차 토큰 만료 후 재응모 (6분 경과)

```
사용자 액션                    서버 처리                         Redis 상태
─────────────────────────────────────────────────────────────────────────────
상황: 이전에 응모 후 6분 경과 (1차 토큰 만료)

1. 재응모 버튼 클릭
                    ┌─────────────────────────────────┐  ┌─────────────────┐
                    │ POST /api/v1/entries            │  │ (1차 토큰 없음) │
                    │                                 │  │ TTL 만료로 삭제 │
                    │ 1. 1차 토큰 생성 (결정론적)    │  └─────────────────┘
                    │    token = "MToxMDA6YTdm..."    │
                    │                                 │  ┌─────────────────┐
                    │ 2. Redis 조회                   │  │ campaignActivity│
                    │    → 없음 (TTL 만료)           │  │ :100:participants│
                    │                                 │  │  └─ userId=1    │
                    │ 3. FCFS 검증 실행               │  │  (여전히 존재)  │
                    │    - Redis Set 확인             │  └─────────────────┘
                    │    → 이미 존재! (DUPLICATED)   │
                    │                                 │
                    └─────────────────────────────────┘

  Response:
  409 Conflict (이미 응모하셨습니다)

─────────────────────────────────────────────────────────────────────────────
```

---

### 4. 결제 중 1차 토큰 만료 (2차 토큰으로 완료)

```
사용자 액션                    서버 처리                         Redis 상태
─────────────────────────────────────────────────────────────────────────────
상황: 응모 → /prepare → PG사 결제 페이지에서 6분 소요

1. 응모 완료
   (1차 토큰: TTL 5분)                                   ┌─────────────────┐
   (2차 토큰: TTL 30분)                                  │ 1차: TTL 300초  │
                                                          │ 2차: TTL 1800초 │
                                                          └─────────────────┘

2. PG사 결제 페이지에서 6분 소요
   (1차 토큰 만료)                                       ┌─────────────────┐
                                                          │ 1차: 없음 (만료)│
                                                          │ 2차: TTL 1440초 │
                                                          └─────────────────┘

3. 결제 완료 후 /confirm 호출
                    ┌─────────────────────────────────┐
                    │ POST /api/v1/payments/confirm   │
                    │                                 │
                    │ 1. 2차 토큰 검증                │
                    │    → 존재 ✅ (30분 TTL)        │
                    │                                 │
                    │ 2. Payload에서 데이터 복원      │
                    │    (1차 토큰 정보 포함)        │
                    │                                 │
                    │ 3. Kafka 전송 성공 ✅          │
                    └─────────────────────────────────┘

  Response:
  { "reservationResult": { "status": "SUCCESS" } }

  → 1차 토큰 만료에도 불구하고 정상 처리! ✅
─────────────────────────────────────────────────────────────────────────────
```

---

## 보안 설계

### 1. HMAC 서명 (위변조 방지)

#### 동작 원리
```java
// 토큰 생성
String payload = "1:100";
String signature = HMAC-SHA256(SECRET_KEY, payload);
// signature = "a7f3b9c2d1e4f5a6b7c8d9e0f1a2b3c4..."

String token = Base64("1:100:a7f3b9c2d1e4...")
```

#### 검증 과정
```java
// 토큰 검증
String decoded = Base64Decode(token);
// "1:100:a7f3b9c2d1e4..."

String[] parts = decoded.split(":");
// ["1", "100", "a7f3b9c2d1e4..."]

String expectedSignature = HMAC-SHA256(SECRET_KEY, "1:100");

if (parts[2].equals(expectedSignature)) {
    // 유효한 토큰
} else {
    // 위조된 토큰 ❌
}
```

#### 보안 강도
- **SECRET_KEY 유출 방지**: 환경변수로 관리, Git 커밋 금지
- **알고리즘**: HMAC-SHA256 (256비트, 산업 표준)
- **서명 길이**: 64자 16진수 (무차별 대입 공격 불가능)

---

### 2. 사용자 소유권 검증

#### /prepare 단계
```java
@PostMapping("/prepare")
public ResponseEntity<?> preparePayment(...) {
    long currentUserId = Long.parseLong(userDetails.getUsername());

    Optional<ReservationTokenPayload> payload =
        reservationTokenService.getPayloadFromToken(token);

    if (payload.isEmpty()) {
        return 403 Forbidden;  // 토큰 만료
    }

    if (payload.get().getUserId() != currentUserId) {
        return 403 Forbidden;  // 토큰 탈취 시도 ❌
    }

    // 정상 처리
}
```

#### /confirm 단계
```java
@PostMapping("/confirm")
public ResponseEntity<?> confirmPayment(...) {
    long currentUserId = Long.parseLong(userDetails.getUsername());

    Optional<PaymentApprovalPayload> payload =
        reservationTokenService.getApprovalPayload(token);

    if (payload.get().getUserId() != currentUserId) {
        return 400 Bad Request;  // 토큰 탈취 시도 ❌
    }

    // Kafka 전송
}
```

#### 공격 시나리오
```
공격자(userId=2)가 userId=1의 토큰 탈취:

1. 공격자가 /prepare 호출
   → currentUserId = 2
   → payload.userId = 1
   → 불일치 → 403 Forbidden ❌

2. 공격자가 /confirm 호출
   → currentUserId = 2
   → payload.userId = 1
   → 불일치 → 400 Bad Request ❌
```

---

### 3. TTL 기반 시간 제한

| 토큰 | TTL | 갱신 가능 | 목적 |
|------|-----|----------|------|
| 1차 토큰 | 5분 | ❌ 불가 | 선착순 슬롯 점유 시간 제한 |
| 2차 토큰 | 30분 | ✅ 가능 | 결제 완료까지 충분한 시간 |

#### 1차 토큰 TTL 고정 이유
```
만약 1차 토큰을 갱신한다면?

사용자 A: 응모 성공
  → 1차 토큰 발급 (5분)
  → 4분 50초 후 재응모
  → TTL 5분 리셋
  → 계속 반복...
  → 무한정 슬롯 점유 ❌

따라서:
- 1차 토큰은 TTL 갱신 불가
- 5분 경과 시 자동 만료
- 선착순 슬롯 해제 (다른 사용자에게 기회)
```

#### 2차 토큰 TTL 갱신 이유
```
PG사 결제 페이지에서 시간 소요:
- 카드 정보 입력: 2-3분
- 본인 인증: 1-2분
- 결제 처리: 30초-1분
- 총: 5-10분 가능

따라서:
- 2차 토큰은 TTL 갱신 가능
- /prepare 재호출 시 30분 리셋
- 여러 번 결제 시도 가능
```

---

### 4. Redis 기반 검증 (DB 부하 방지)

#### Redis 키 설계
```
1차 토큰:
  Key: RESERVATION_TOKEN:{token}
  Value: {userId, campaignActivityId, productId, ...}
  TTL: 300초

2차 토큰:
  Key: PAYMENT_APPROVED_TOKEN:{userId}:{campaignActivityId}
  Value: {userId, campaignActivityId, productId, reservationToken, ...}
  TTL: 1800초
```

#### 성능 비교
```
[DB 조회 방식]
/prepare 호출 (10,000 TPS)
  → MySQL SELECT (10,000 쿼리/초)
  → Connection Pool 고갈
  → 응답 시간 증가

[Redis 조회 방식]
/prepare 호출 (10,000 TPS)
  → Redis GET (10,000 명령/초)
  → 평균 1ms 이하
  → 안정적 처리
```

---

## 예외 처리

### 1. 1차 토큰 만료

#### 시나리오
```
사용자 응모 후 5분 경과 → /prepare 호출
```

#### 처리
```java
@PostMapping("/prepare")
public ResponseEntity<?> preparePayment(...) {
    Optional<ReservationTokenPayload> payload =
        reservationTokenService.getPayloadFromToken(token);

    if (payload.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(PaymentPrepareResponse.failure(
                "결제 시간이 만료되었습니다. 처음부터 다시 응모해주세요."
            ));
    }
}
```

#### HTTP 응답
```
403 Forbidden
{
  "success": false,
  "message": "결제 시간이 만료되었습니다. 처음부터 다시 응모해주세요."
}
```

---

### 2. 2차 토큰 만료

#### 시나리오
```
사용자 응모 후 30분 경과 → /confirm 호출
```

#### 처리
```java
@PostMapping("/confirm")
public ResponseEntity<?> confirmPayment(...) {
    Optional<PaymentApprovalPayload> payload =
        reservationTokenService.getApprovalPayload(token);

    if (payload.isEmpty()) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(PaymentConfirmationResponse.failure(
                ReservationResult.error(),
                "결제 시간이 만료되었습니다. 관리자에게 문의해주세요."
            ));
    }
}
```

#### HTTP 응답
```
410 Gone
{
  "reservationResult": { "status": "ERROR" },
  "reason": "결제 시간이 만료되었습니다. 관리자에게 문의해주세요."
}
```

---

### 3. 토큰 탈취 (사용자 불일치)

#### 시나리오
```
공격자(userId=2)가 userId=1의 토큰 사용
```

#### 처리 (/prepare)
```java
if (payload.getUserId() != currentUserId) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(PaymentPrepareResponse.failure("권한이 없습니다."));
}
```

#### 처리 (/confirm)
```java
if (payload.getUserId() != currentUserId) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(PaymentConfirmationResponse.failure(
            ReservationResult.error(),
            "응모자와 요청자가 다릅니다."
        ));
}
```

---

### 4. Kafka 전송 실패

#### 시나리오
```
/confirm 호출 → Kafka 브로커 다운
```

#### 재시도 로직
```java
public boolean sendToKafkaWithRetry(PaymentApprovalPayload payload, int maxRetries) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            kafkaTemplate.send(topic, message);
            return true;
        } catch (Exception e) {
            if (attempt < maxRetries) {
                Thread.sleep(1000L * attempt);  // 지수 백오프: 1초, 2초, 3초
            }
        }
    }
    return false;  // 3회 모두 실패
}
```

#### HTTP 응답
```
503 Service Unavailable
{
  "reservationResult": { "status": "ERROR" },
  "reason": "일시적인 오류로 결제가 취소되었습니다. 처음부터 다시 응모해주세요."
}
```

---

## 성능 최적화

### 1. ThreadLocal HMAC 인스턴스 재사용

#### 문제
```java
// 매번 생성 (느림)
public String generateToken(...) {
    HmacUtils hmac = new HmacUtils(HMAC_SHA_256, SECRET_KEY);  // ← 비용 큼
    return hmac.hmacHex(data);
}

10,000 요청:
  - Mac.getInstance() 10,000회
  - mac.init() 10,000회
  - 총 시간: 450ms
```

#### 해결
```java
// ThreadLocal 재사용 (빠름)
private final ThreadLocal<HmacUtils> hmacUtilsThreadLocal =
    ThreadLocal.withInitial(() ->
        new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET_TOKEN_KEY)
    );

public String generateToken(...) {
    return hmacUtilsThreadLocal.get().hmacHex(data);  // ← 재사용
}

10,000 요청 (200 스레드):
  - Mac.getInstance() 200회 (스레드당 1회)
  - mac.init() 200회
  - 총 시간: 85ms (5.3배 향상!)
```

#### 성능 비교
| 방식 | Mac 생성 횟수 | 10,000 요청 시간 | 개선율 |
|------|--------------|-----------------|--------|
| 매번 생성 | 10,000회 | 450ms | - |
| ThreadLocal | 200회 | 85ms | **5.3배** |

---

### 2. Redis Pipeline (선택적)

#### 일반 방식
```java
Boolean exists = redisTemplate.hasKey(key);
if (exists) {
    redisTemplate.expire(key, TTL, TimeUnit.MINUTES);
} else {
    redisTemplate.opsForValue().set(key, value, TTL, TimeUnit.MINUTES);
}

// 네트워크 왕복: 2-3회
```

#### Pipeline 방식
```java
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    if (connection.exists(keyBytes)) {
        connection.expire(keyBytes, TTL);
    } else {
        connection.setEx(keyBytes, TTL, valueBytes);
    }
    return null;
});

// 네트워크 왕복: 1회
```

---

### 3. 결정론적 토큰 (중복 발급 방지)

#### UUID 방식 (이전)
```java
String token = UUID.randomUUID().toString();

재응모 시:
  → 새로운 UUID 생성
  → Redis 중복 저장
  → 메모리 낭비
```

#### HMAC 결정론적 방식 (현재)
```java
String token = HMAC(SECRET_KEY, "userId:campaignActivityId");

재응모 시:
  → 같은 토큰 생성
  → Redis 조회 (이미 존재)
  → 중복 저장 방지
  → 메모리 효율적
```

---

## 주요 엔드포인트 명세

### POST /api/v1/entries

#### Request
```json
{
  "campaignActivityId": 100,
  "productId": 200,
  "campaignActivityType": "FIRST_COME_FIRST_SERVE"
}
```

#### Response (성공)
```json
200 OK
{
  "reservationToken": "MToxMDA6YTdmM2I5YzJkMWU0ZjVhNmI3YzhkOWUwZjFhMmIzYzQ..."
}
```

#### Response (재결제 시나리오)
```json
200 OK
{
  "reservationToken": "MToxMDA6YTdm..."  // 이전과 동일한 토큰
}
```

---

### POST /api/v1/payments/prepare

#### Request
```json
{
  "reservationToken": "MToxMDA6YTdm..."
}
```

#### Response (성공)
```json
200 OK
{
  "success": true,
  "message": "결제를 진행해주세요.",
  "approvalToken": "1:100"
}
```

#### Response (TTL 갱신)
```json
200 OK
{
  "success": true,
  "message": "결제를 진행해주세요.",
  "approvalToken": "1:100"  // 동일, TTL만 30분 리셋
}
```

---

### POST /api/v1/payments/confirm

#### Request
```json
{
  "reservationToken": "1:100"  // 2차 토큰
}
```

#### Response (성공)
```json
200 OK
{
  "reservationResult": {
    "status": "SUCCESS",
    "order": null
  }
}
```

---

## 테스트 시나리오

### 1. 정상 플로우
```
응모 → 1차 토큰 발급 → /prepare → 2차 토큰 발급 → /confirm → Kafka 전송 성공
```

### 2. 1차 토큰 만료
```
1차 토큰 발급 → 5분 경과 → /prepare 호출 → 403 Forbidden
```

### 3. 토큰 탈취 (prepare)
```
userId=1 토큰 발급 → userId=2가 /prepare 호출 → 403 Forbidden
```

### 4. 토큰 탈취 (confirm)
```
userId=1 2차 토큰 → userId=2가 /confirm 호출 → 400 Bad Request
```

### 5. Kafka 전송 실패
```
/confirm 호출 → Kafka 3회 재시도 실패 → 503 Service Unavailable
```

### 6. 재결제 - Entry 검증 스킵
```
1차 응모 → 1차 토큰 발급 → 재응모 → Redis 조회 (존재) → 검증 스킵 → 기존 토큰 반환
```

### 7. 2차 토큰 TTL 갱신
```
/prepare 호출 → 2차 토큰 발급 → /prepare 재호출 → TTL 30분 리셋
```

### 8. 결정론적 토큰 검증
```
같은 userId + campaignActivityId → 같은 토큰 생성
다른 userId → 다른 토큰 생성
```

---

## 설정 파일

### application.yml
```yaml
payment:
  token:
    secret: ${PAYMENT_TOKEN_SECRET:change-this-in-production-must-be-at-least-32-chars}

spring:
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 3000ms
```

### 환경변수 (운영)
```bash
export PAYMENT_TOKEN_SECRET="$(openssl rand -base64 32)"
```

---

## 참고 문서

- [purchase-event-flow.md](./purchase-event-flow.md) - 구매 이벤트 전체 플로우
- [campaign-activity-limit-flow.md](./campaign-activity-limit-flow.md) - FCFS 선착순 플로우
- [redis-fast-validation-plan.md](./redis-fast-validation-plan.md) - Redis 빠른 검증
