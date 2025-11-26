# 실시간 선착순 이벤트 아키텍처 최종 설계 및 개발 계획

## 1. 최종 목표

'선착순 응모 성공 시 즉시 결제'로 이어지는 비즈니스 요구사항을 만족시키기 위해, 아래 세 가지 상충하는 요구사항을 모두 충족하는 안정적이고 확장 가능한 실시간 선착순 이벤트 시스템을 구축한다.

1.  **즉각적인 확정 응답:** 사용자는 응모 즉시 최종 성공/실패 여부를 알아야 한다. '처리 중' 상태는 허용되지 않는다.
2.  **자원의 낭비 없음:** 자격 미달인 사용자가 한정된 선착순 자리를 차지했다가 나중에 취소되는 '유령 자리(Ghost Slot)'가 발생해서는 안 된다.
3.  **대용량 트래픽 처리:** 이벤트 시작 시점의 대규모 동시 요청을 안정적으로 처리해야 한다.

## 2. 아키텍처 설계 진화 과정 및 최종 결정

### 2.1. 초기 아이디어: 완전 비동기 (Kafka 우선)

-   **개념:** `entry-service`는 모든 요청을 즉시 Kafka에 발행하고, `core-service`의 Consumer가 나중에 모든 검증과 처리를 담당한다.
-   **장점:** 대용량 트래픽을 안정적으로 수용할 수 있다.
-   **치명적 단점:**
    -   사용자가 결과를 즉시 알 수 없어 '결제 페이지 이동'이 불가능하다.
    -   부적격자가 자리를 선점하는 '유령 자리' 문제가 발생한다.
-   **결론:** **폐기**. 핵심 비즈니스 요구사항을 만족시키지 못한다.

### 2.2. 중간 아이디어: '공정성' vs '즉각적 피드백'의 딜레마

-   **논의:** Kafka의 순서 보장을 통한 '완벽한 공정성'과, 동기 API 호출을 통한 '즉각적인 피드백' 사이에서 트레이드오프를 논의했다.
-   **결론:** 대부분의 대규모 선착순 시스템(티켓팅 등)과 마찬가지로, 밀리초 단위의 완벽한 공정성보다 **'내 자리가 지금 확정되었는가'를 즉시 아는 사용자 경험의 가치가 비즈니스적으로 훨씬 더 크다**고 판단했다.

### 2.3. 최종 아키텍처: '동기 검증 후 원자적 선점'

위 논의를 바탕으로, 아래와 같은 동기 처리 기반의 아키텍처를 최종 확정한다.

-   **`entry-service`의 역할 (관문 및 실행자):**
    1.  사용자의 응모 API 요청을 받는다.
    2.  **빠른 사전 검증:** Redis 캐시를 조회하여 '사용자 등급/지역' 등 빠르게 확인할 수 있는 조건을 먼저 검증한다.
    3.  **무거운 최종 검증:** `core-service`에 내부 API로 동기 호출하여 '월간 구매 횟수' 등 DB 조회가 필요한 조건을 검증한다.
    4.  **원자적 자리 선점:** 모든 검증 통과 시, Redis의 `INCR` 명령어로 원자적으로 선착순 자리를 확정한다.
    5.  사용자에게 '최종 성공' 또는 '실패/매진' 결과를 즉시 동기적으로 응답한다.
    6.  **최종 성공 건**에 대해서만 Kafka로 '응모 확정' 이벤트를 발행하여 후처리를 위임한다.

-   **`core-service`의 역할 (데이터 저장소 및 검증 서비스):**
    1.  `entry-service`가 호출할 '무거운 최종 검증'을 위한 내부 API를 제공한다.
    2.  주기적으로 배치(Batch) Job을 실행하여 `user_metric` 데이터를 최신으로 유지한다.
    3.  `entry-service`가 발행한 '응모 확정' 이벤트를 Kafka Consumer로 구독하여, `campaign_activity_entry` 같은 최종 이력을 DB에 기록한다.

## 3. 동적 검증 엔진 상세 설계 (`core-service`)

캠페인별로 달라지는 다중 참여 조건을 유연하게 처리하기 위해 **전략 패턴(Strategy Pattern)** 기반의 동적 검증 엔진을 설계한다.

### 3.1. JSON Payload 구조

-   캠페인 활동의 `filters` 컬럼에는 아래와 같은 JSON 배열이 저장된다.
-   `DynamicValidationService`는 이 배열의 모든 규칙을 **AND** 관계로 처리한다.

```json
[
  {
    "type": "PURCHASE_DATE",
    "operator": "BETWEEN",
    "values": ["2025-10-01T00:00:00Z", "2025-10-31T23:59:59Z"]
  },
  {
    "type": "AGE",
    "operator": "LTE",
    "values": ["20"]
  },
  {
    "type": "AGE",
    "operator": "NOT_IN",
    "values": ["15"]
  }
]
```

### 3.2. 핵심 컴포넌트 및 최종 코드

#### 1. `ValidationLimitStrategy.java` (인터페이스)
-   **역할:** 모든 검증 규칙 클래스가 구현해야 할 공통 규격. `type` 이름을 반환하고, `validate` 로직을 정의한다.
-   **코드:**
    ```java
    package com.axon.core_service.service.validation.CampaignActivityLimit;

    import com.fasterxml.jackson.databind.JsonNode;
    import java.util.List;

    public interface ValidationLimitStrategy {
        String getLimitName();
        boolean validateCampaignActivityLimit(Long userId, String operator, List<String> values);
    }
    ```

#### 2. `PurchaseDateRule.java` (규칙 구현체 예시)
-   **역할:** `type`이 "PURCHASE_DATE"인 규칙의 검증 로직을 실제로 수행. `operator`에 따라 내부적으로 분기 처리.
-   **코드:**
    ```java
    package com.axon.core_service.service.validation.rule;

    import com.axon.core_service.domain.user.UserSummary;
    import com.axon.core_service.repository.UserSummaryRepository;
    import com.axon.core_service.service.validation.CampaignActivityLimit.ValidationLimitStrategy;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Component;

    import java.time.Instant;
    import java.util.List;
    import java.util.Optional;

    @Component
    @RequiredArgsConstructor
    public class PurchaseDateRule implements ValidationLimitStrategy {

        private final UserSummaryRepository userSummaryRepository;

        @Override
        public String getLimitName() {
            return "PURCHASE_DATE";
        }

        @Override
        public boolean validateCampaignActivityLimit(Long userId, String operator, List<String> values) {
            Optional<UserSummary> userSummaryOpt = userSummaryRepository.findById(userId);
            if (userSummaryOpt.isEmpty() || userSummaryOpt.get().getLastPurchaseAt() == null) {
                return false;
            }
            Instant lastPurchaseAt = userSummaryOpt.get().getLastPurchaseAt();

            if (values == null || values.isEmpty()) {
                return false;
            }
            
            Instant startDateTime = Instant.parse(values.get(0));
            Instant endDateTime = (values.size() == 2) ? Instant.parse(values.get(1)) : null;

            switch (operator) {
                case "BETWEEN":
                    if (endDateTime == null) return false;
                    return !lastPurchaseAt.isBefore(startDateTime) && !lastPurchaseAt.isAfter(endDateTime);
                case "NOT_BETWEEN":
                    if (endDateTime == null) return false;
                    return lastPurchaseAt.isBefore(startDateTime) || lastPurchaseAt.isAfter(endDateTime);
                case "GTE":
                    return !lastPurchaseAt.isBefore(startDateTime);
                case "LTE":
                    return !lastPurchaseAt.isAfter(startDateTime);
                default:
                    return false;
            }
        }
    }
    ```

#### 3. `ValidationRuleFactory.java` (팩토리)
-   **역할:** Spring 컨테이너에 등록된 모든 `ValidationLimitStrategy` 빈들을 수집하여, `type` 이름으로 쉽게 찾아주는 역할.
-   **코드:**
    ```java
    package com.axon.core_service.service.validation.CampaignActivityLimit;

    import org.springframework.stereotype.Component;
    import java.util.List;
    import java.util.Map;
    import java.util.function.Function;
    import java.util.stream.Collectors;

    @Component
    public class ValidationRuleFactory {
        private final Map<String, ValidationLimitStrategy> strategyMap;

        public ValidationRuleFactory(List<ValidationLimitStrategy> strategies) {
            this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ValidationLimitStrategy::getLimitName, Function.identity()));
        }

        public ValidationLimitStrategy getStrategy(String limitName) {
            return strategyMap.get(limitName);
        }
    }
    ```

#### 4. `DynamicValidationService.java` (총괄 서비스)
-   **역할:** 컨트롤러의 요청을 받아, DB에서 캠페인 활동의 `filters`를 조회하고, `ValidationRuleFactory`를 통해 적절한 규칙을 찾아 순서대로 실행.
-   **코드:**
    ```java
    package com.axon.core_service.service.validation.CampaignActivityLimit;

    import com.axon.core_service.domain.campaignactivity.CampaignActivity;
    import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
    import com.axon.core_service.repository.CampaignActivityRepository;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import java.util.List;

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class DynamicValidationService {

        private final CampaignActivityRepository campaignActivityRepository;
        private final ValidationRuleFactory validationRuleFactory;

        @Transactional(readOnly = true)
        public boolean validate(Long userId, Long campaignActivityId) {
            CampaignActivity activity = campaignActivityRepository.findById(campaignActivityId)
                    .orElseThrow(() -> new IllegalArgumentException("CampaignActivity not found: " + campaignActivityId));

            List<FilterDetail> rules = activity.getFilters();
            if (rules == null || rules.isEmpty()) {
                return true;
            }

            for (FilterDetail ruleDetail : rules) {
                String ruleName = ruleDetail.getType();
                String operator = ruleDetail.getOperator() != null ? ruleDetail.getOperator() : "BETWEEN";
                List<String> values = ruleDetail.getValues();

                ValidationLimitStrategy strategy = validationRuleFactory.getStrategy(ruleName);
                if (strategy == null) {
                    log.warn("Undefined validation strategy: {}", ruleName);
                    continue;
                }

                if (!strategy.validateCampaignActivityLimit(userId, operator, values)) {
                    log.info("User {} failed validation for rule: {}", userId, ruleName);
                    return false;
                }
            }
            return true;
        }
    }
    ```

## 4. 상세 구현 계획 (To-Do List)

-   `[x]`는 설계 또는 구현 완료, `[ ]`는 구현 필요.

### Phase 1: `core-service` - 동적 검증 엔진 및 API 구현
-   `[x]` **설계:** 전략 패턴 기반 동적 검증 엔진 설계 확정.
-   `[x]` **구현:** `ValidationLimitStrategy`, `PurchaseDateRule`, `ValidationRuleFactory`, `DynamicValidationService`, `ValidationController` 등 관련 컴포넌트 구현 완료.

### Phase 2: `entry-service` - 검증 및 선점 로직 구현
-   `[x]` **설계:** `WebClient`를 이용한 동기 API 호출 방식 확정.
-   `[ ]` **1. 의존성 추가:** `build.gradle`에 `spring-boot-starter-webflux` 추가.
-   `[ ]` **2. `WebClient` 빈 설정:** `WebClientConfig.java`에 `core-service` 호출용 `WebClient` 빈 등록 (기존 설정 재사용 가능).
-   `[ ]` **3. API 호출 서비스 생성:** `CoreValidationService.java` 생성 및 `isUserEligible` 메서드 구현.
-   `[ ]` **4. 컨트롤러 수정:** `EntryController`에 `CoreValidationService`를 주입하고, '무거운 최종 검증' 로직 추가.
-   `[ ]` **5. 원자적 선점 로직 구현:** `EntryReservationService`에서 Redis `INCR`을 사용한 선점 로직 구현.
-   `[ ]` **6. Kafka 발행 로직 구현:** 최종 성공 건에 대해서만 Kafka로 이벤트 발행.

### Phase 3: 데이터 파이프라인 (Batch Job 수정)
-   `[x]` **설계:** `user_metric` 생성을 위한 배치 Job 설계 완료.
-   `[ ]` **구현:** `monthlyPurchaseJob`에 `user_metric` 데이터를 Redis 캐시에 복제(with TTL)하는 **Step 2** 추가.

### Phase 4: `core-service` - 후처리 컨슈머 구현
-   `[ ]` `entry-service`가 발행한 '최종 성공' 이벤트를 구독하는 Kafka Consumer 구현.
-   `[ ]` `campaign_activity_entry` 테이블에 최종 이력 저장.

## 5. 주요 논의 및 해결된 이슈 기록

-   **`isSuccess` vs `eligible`:** Jackson 라이브러리가 `is`로 시작하는 boolean 필드를 직렬화/역직렬화하는 과정에서 발생하는 문제를 피하기 위해, DTO의 필드명을 `isSuccess`에서 `eligible`로 리팩토링했으나, 최종적으로는 `@JsonProperty`를 사용하거나 혼동 없는 다른 이름으로 통일하는 것이 좋다는 결론에 도달. 현재는 `isSuccess`로 다시 통일된 상태.
-   **`401 Unauthorized` 에러:** `entry-service`가 `core-service`로 보낸 요청의 `Authorization` 헤더가 `null`로 수신되는 문제. `core-service`의 `SecurityConfig`에서 `JwtAuthenticationFilter`의 순서를 `UsernamePasswordAuthenticationFilter` 앞으로 조정하여 해결.
-   **`UnrecognizedPropertyException`:** DB에 저장된 `filters` JSON의 `operator` 필드를 `FilterDetail` DTO가 인식하지 못하는 문제. `FilterDetail` 클래스에 `operator` 필드를 추가하여 해결.
-   **`NullPointerException` on `values.isArray()`:** `DynamicValidationService`가 `ValidationLimitStrategy`에 `values` 배열만 넘겨주어 발생. `operator`와 `values`를 모두 포함한 `FilterDetail` 객체 전체를 `JsonNode`로 변환하여 넘기거나, 각각의 인자로 넘겨주는 방식으로 수정하여 해결. 최종적으로 후자를 채택.