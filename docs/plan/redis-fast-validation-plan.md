# Redis 및 동기 API를 이용한 다단계 검증 아키텍처

## 1. 최종 목표

`entry-service`가 응모 요청을 받았을 때, 여러 단계의 검증을 효율적으로 수행하여 **즉각적인 성공/실패 피드백**을 제공하고, **자원 낭비를 최소화**하며, **대용량 트래픽을 안정적으로 처리**하는 것을 목표로 한다.

## 2. 확정된 아키텍처: '다단계 동기 검증 후 원자적 선점'

사용자에게 즉각적인 피드백을 주기 위해, 모든 자격 검증은 API 요청에 대한 응답이 가기 전에 **동기적으로** 완료된다. 검증은 '빠른 검증'과 '무거운 검증'으로 나뉘며, 불필요한 검증은 건너뛰어 성능을 최적화한다.

```
                               +--------------------------+      +------------------+
                               | entry-service            |      | core-service     |
                               |                          |      |                  |
                  +----------->| CampaignActivityMetaSvc  |----->| GET /activities/{id} |
                  |            | (Cache Miss 시 1회 호출) |<-----| (filters 포함 응답)|
                  |            +--------------------------+      +------------------+
                  |
+------+      +------------------+      +-----------------+
| User |----->| EntryController  |----->| Redis           |
+------+      | (요청 접수)      |      | (1. Meta 캐시)  |
              |                  |      | (2. User 캐시)  |
              | 1. Meta 정보 요청 |      +-----------------+
              | 2. 빠른 검증 수행 |
              | 3. 무거운 검증 요청|
              | 4. 원자적 선점     |
              +------------------+
```

### 2.1. 데이터 흐름 및 역할

1.  **`CampaignActivityMetaService` (in `entry-service`):**
    -   캠페인 정보(특히 `filters`)가 필요할 때, 먼저 Redis 캐시(`campaign-meta:{id}`)를 확인한다.
    -   캐시에 없으면 `core-service` API를 호출하여 정보를 가져온 뒤, **규칙 요약 정보(`hasFastValidation`, `hasHeavyValidation`)를 계산**하여 `CampaignActivityMeta` 객체를 만들고, 이를 Redis에 짧은 시간(예: 1분) 동안 캐시한다.

2.  **`EntryController` (in `entry-service`):**
    -   `CampaignActivityMetaService`로부터 `meta` 정보를 받는다.
    -   `meta.hasFastValidation()`이 `true`일 경우에만, `FastValidationService`를 호출하여 **빠른 검증**을 수행한다.
    -   `meta.hasHeavyValidation()`이 `true`일 경우에만, `CoreValidationService`를 호출하여 **무거운 검증**을 수행한다.
    -   모든 검증 통과 시, `EntryReservationService`를 호출하여 **원자적 자리 선점**을 시도한다.
    -   각 단계의 실패 사유를 사용자에게 명확하게 응답한다.

### 2.2. '빠른 검증'과 '무거운 검증'의 분리

-   **`filters` JSON 구조:** 각 규칙에 `phase` 필드를 추가하여 검증 단계를 명시한다.
    ```json
    [
      {"type": "GRADE", "operator": "IN", "values": ["VIP"], "phase": "fast"},
      {"type": "MONTHLY_PURCHASE", "operator": "LTE", "values": ["5"], "phase": "heavy"}
    ]
    ```
-   **`FastValidationService` (in `entry-service`):** `phase`가 "fast"인 규칙만 처리한다. Redis에 캐시된 `UserCacheDto`를 사용하여 검증한다.
-   **`DynamicValidationService` (in `core-service`):** `phase`가 "heavy"인 규칙만 처리한다. `core-service`의 DB를 직접 조회하여 검증한다.

## 3. 상세 구현 계획 (To-Do List)

-   `[x]`는 완료, `[ ]`는 시작 전을 의미합니다.

### Phase 1: `core-service` - 데이터 구조 및 검증 엔진 확장
-   `[x]` **`User` 엔티티 확장:** `age`, `grade` 필드 추가 및 기본값(`BRONZE`) 설정 완료.
-   `[x]` **`FilterDetail` DTO 확장:** `phase` 필드 추가 완료.
-   `[x]` **동적 검증 엔진 구현:** 전략 패턴 기반의 `DynamicValidationService` 및 관련 컴포넌트 구현 완료.
-   `[x]` **내부 검증 API 구현:** `ValidationController` 구현 완료.
-   `[x]` **로그인 시 캐시 저장:** `OAuth2AuthenticationSuccessHandler`에서 `UserCacheDto`를 Redis에 저장하는 기능 구현 완료.

**=> 진행률: 100% 완료**

---

### Phase 2: `entry-service` - 다단계 검증 및 선점 로직 구현
-   `[x]` **`RedisConfig` 설정:** 객체 저장을 위한 `RedisTemplate<String, Object>` 빈 설정 완료.
-   `[x]` **`FastValidationService` 구현:** '빠른 검증' 로직 및 실패 시 예외 발생 기능 구현 완료.
-   `[x]` **`CoreValidationService` 구현:** `WebClient`를 이용한 '무거운 검증' API 호출 로직 구현 완료.
-   `[x]` **`CampaignActivityMeta` 확장:** `filters` 및 `hasFast/HeavyValidation` 필드 추가 완료.
-   `[x]` **`CampaignActivityMetaService` 수정:** `meta` 정보 캐싱 및 '요약 정보' 계산 로직 구현 완료.
-   `[x]` **`EntryController` 수정:** `meta`의 요약 정보를 사용하여 각 검증 서비스를 조건부로 호출하고, 예외를 처리하는 로직 구현 완료.
-   `[ ]` **원자적 선점 로직 확인:** `EntryReservationService`가 `RedisTemplate<String, Object>`를 사용하도록 수정되었는지, 또는 `StringRedisTemplate`으로도 문제없이 동작하는지 최종 확인.
-   `[ ]` **Kafka 발행:** 최종 성공 건에 대해서만 Kafka로 '응모 확정' 이벤트 발행.

**=> 진행률: 약 80% 진행**

---

### Phase 3 & 4: 후속 처리 (시작 전)
-   `[ ]` **데이터 파이프라인:** 배치 Job에 `user_metric` 데이터 Redis 복제 Step 추가.
-   `[ ]` **후처리 컨슈머:** `core-service`에 '응모 확정' 이벤트를 처리하여 DB에 최종 기록하는 Kafka Consumer 구현.

## 4. 주요 해결된 이슈

-   **`RedisTemplate` 빈 주입 실패:** `@Primary` 또는 `@Qualifier`를 사용하는 대신, 각 서비스(`core-service`, `entry-service`)에 명확한 타입의 `RedisTemplate<String, Object>` 빈을 생성하는 `RedisConfig`를 각각 추가하여 해결.
-   **`SerializationException`:** `UserCacheDto`가 `Serializable`이 아니어서 발생. `GenericJackson2JsonRedisSerializer`를 사용하는 `RedisConfig`를 통해 JSON 직렬화 방식으로 변경하여 해결.
-   **`401 Unauthorized`:** `core-service`의 `SecurityConfig`에서 `JwtAuthenticationFilter`의 순서를 조정하여, `Authorization` 헤더가 유실되는 문제를 해결.
-   **JSON 역직렬화 실패:** `isSuccess` 필드명 문제 및 `UserCacheDto`의 기본 생성자 부재 문제. `@JsonProperty` 또는 필드명 변경, `@NoArgsConstructor` 추가로 해결.
-   **`Table ... doesn't exist`:** `ddl-auto`가 동작하지 않는 문제. `defer-datasource-initialization: true` 설정 및 `schema.sql` 또는 `BatchInitialTableConfig`를 통해 해결.
-   **`Invalid default value`:** `@ColumnDefault`와 Enum 타입의 DDL 생성 충돌. `@Builder.Default`를 사용하는 애플리케이션 레벨 기본값 설정으로 변경하여 해결.