# LLM 인사이트 기능 추가 계획

## 1. 현황 분석

### 기존 구현 완료 사항 ✅
- **Gemini 2.0 Flash 통합**: `GeminiLLMQueryService`로 이미 구현됨
- **데이터 수집**: `DashboardService`, `CohortAnalysisService` 연동 완료
- **질의응답 기능**: 사용자 질문에 대한 통계 기반 답변 제공 중
- **3개 레벨 지원**: Campaign, Activity, Global

### 추가할 인사이트 기능
기존 질의응답과 달리, **자동으로 캠페인 성과를 분석하고 실행 가능한 권장사항을 제공**하는 기능:

1. **성공/실패 평가**: 자동으로 캠페인 성과 판단
2. **근본 원인 분석**: 왜 성공했는지/실패했는지 데이터 기반 분석
3. **실행 가능한 권장사항**: 다음 액션 아이템 제시
4. **벤치마크 비교**: 업계 표준 대비 현재 성과 평가

---

## 2. 구현 설계

### 2.1 인터페이스 확장

**기존 `LLMQueryService` 인터페이스**:
```java
public interface LLMQueryService {
    DashboardQueryResponse processQuery(Long campaignId, String query);
    DashboardQueryResponse processQueryByActivity(Long activityId, String query);
    DashboardQueryResponse processGlobalQuery(String query);
}
```

**추가할 메서드**:
```java
public interface LLMQueryService {
    // 기존 메서드...

    /**
     * 캠페인에 대한 자동 인사이트 생성
     * @param campaignId 캠페인 ID
     * @return 구조화된 인사이트 (성공/실패 원인, 권장사항 등)
     */
    CampaignInsightResponse generateCampaignInsights(Long campaignId);

    /**
     * Activity에 대한 자동 인사이트 생성
     * @param activityId Activity ID
     * @return 구조화된 인사이트
     */
    CampaignInsightResponse generateActivityInsights(Long activityId);
}
```

### 2.2 새로운 응답 DTO

```java
package com.axon.core_service.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 인사이트 분석 응답
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignInsightResponse {

    /**
     * 전체 평가: SUCCESS, MIXED, FAILURE
     */
    private String overallAssessment;

    /**
     * 핵심 발견사항
     */
    private List<KeyFinding> keyFindings;

    /**
     * 근본 원인 분석
     */
    private List<RootCause> rootCauses;

    /**
     * 실행 가능한 권장사항
     */
    private List<Recommendation> recommendations;

    /**
     * 벤치마크 비교
     */
    private BenchmarkComparison benchmarks;

    /**
     * 원본 데이터 (디버깅용)
     */
    private Object rawData;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyFinding {
        private String metric;       // 예: "LTV/CAC 비율"
        private String value;        // 예: "1.8"
        private String interpretation; // 예: "건강한 임계값 3.0보다 낮음"
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootCause {
        private String issue;   // 예: "높은 결제 이탈률"
        private String evidence; // 예: "65%가 체크아웃에서 이탈 (ES 데이터)"
        private String impact;  // HIGH, MEDIUM, LOW
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String action;    // 예: "체크아웃 플로우 간소화"
        private String rationale; // 예: "업계 데이터상 30% 전환율 향상 기대"
        private int priority;     // 1-5 (1이 최우선)
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkComparison {
        private Double ltvCacRatio;     // 실제 LTV/CAC
        private String ltvCacStatus;    // "BELOW_BENCHMARK" | "MEETS_BENCHMARK" | "EXCEEDS_BENCHMARK"
        private Double repeatPurchaseRate;
        private String repeatStatus;
        private Double fillRate;
        private String fillStatus;
    }
}
```

### 2.3 GeminiLLMQueryService 확장

```java
@Override
public CampaignInsightResponse generateCampaignInsights(Long campaignId) {
    log.info("Generating insights for campaign: {}", campaignId);

    // 1. 기존 데이터 수집 로직 재사용
    CampaignDashboardResponse dashboardData = dashboardService.getDashboardByCampaign(campaignId);

    // 2. Cohort 데이터 수집 (대표 Activity 사용)
    Object cohortData = null;
    if (!dashboardData.activities().isEmpty()) {
        Long representativeActivityId = dashboardData.activities().get(0).activityId();
        cohortData = cohortAnalysisService.analyzeCohortByActivity(representativeActivityId, null, null);
    }

    // 3. 컨텍스트 직렬화
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("dashboard", dashboardData);
    if (cohortData != null) {
        contextMap.put("cohortAnalysis", cohortData);
    }
    String contextJson = serializeData(contextMap);

    // 4. 인사이트 전용 프롬프트 생성
    String prompt = buildInsightPrompt(contextJson);

    // 5. Gemini API 호출
    String geminiResponse = callGeminiApi(prompt);

    // 6. JSON 파싱 및 응답 생성
    return parseInsightResponse(geminiResponse, dashboardData, cohortData);
}

@Override
public CampaignInsightResponse generateActivityInsights(Long activityId) {
    log.info("Generating insights for activity: {}", activityId);

    // 1. Activity 데이터 수집
    DashboardResponse dashboardData = dashboardService.getDashboardByActivity(
        activityId, DashboardPeriod.SEVEN_DAYS, null, null
    );

    // 2. Cohort 데이터 수집
    Object cohortData = cohortAnalysisService.analyzeCohortByActivity(activityId, null, null);

    // 3. 컨텍스트 직렬화
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("dashboard", dashboardData);
    contextMap.put("cohortAnalysis", cohortData);
    String contextJson = serializeData(contextMap);

    // 4. 인사이트 프롬프트 생성 및 호출
    String prompt = buildInsightPrompt(contextJson);
    String geminiResponse = callGeminiApi(prompt);

    // 5. 응답 파싱
    return parseInsightResponse(geminiResponse, dashboardData, cohortData);
}
```

### 2.4 인사이트 전용 프롬프트

```java
private String buildInsightPrompt(String dataContext) {
    return """
        당신은 이커머스 마케팅 전문가입니다. 제공된 캠페인 데이터를 분석하여 **자동 인사이트**를 생성하세요.

        # 제공된 데이터
        %s

        # 분석 요구사항

        ## 1. 전체 평가 (overallAssessment)
        - 다음 중 하나로 평가: "SUCCESS", "MIXED", "FAILURE"
        - 판단 기준:
          * LTV/CAC 비율 >= 3.0 && 재구매율 >= 25%% → SUCCESS
          * LTV/CAC 비율 >= 1.5 && 재구매율 >= 15%% → MIXED
          * 그 외 → FAILURE

        ## 2. 핵심 발견사항 (keyFindings)
        - 3-5개의 주요 메트릭 분석
        - 각 항목: metric (메트릭 이름), value (값), interpretation (해석)
        - 예시:
          * metric: "LTV/CAC 비율"
          * value: "1.8"
          * interpretation: "건강한 임계값 3.0보다 낮아 고객 획득 비용 대비 수익이 부족함"

        ## 3. 근본 원인 분석 (rootCauses)
        - 성공/실패의 주요 원인 2-4개 도출
        - 각 항목: issue (문제), evidence (증거), impact (HIGH/MEDIUM/LOW)
        - 데이터에 근거한 구체적인 증거 필수
        - 예시:
          * issue: "높은 결제 이탈률"
          * evidence: "평균 전환율 대비 35%% 낮은 수치 기록"
          * impact: "HIGH"

        ## 4. 실행 가능한 권장사항 (recommendations)
        - 우선순위가 높은 액션 아이템 3-5개
        - 각 항목: action (행동), rationale (근거), priority (1-5, 1이 최우선)
        - 구체적이고 실행 가능한 내용
        - 예시:
          * action: "첫 구매 고객 대상 7일 내 재구매 쿠폰 발송"
          * rationale: "현재 재구매율 12%%로 업계 평균 25%% 대비 낮음. 초기 리텐션 강화 필요"
          * priority: 1

        ## 5. 벤치마크 비교 (benchmarks)
        - 다음 메트릭 평가:
          * ltvCacRatio: 실제 값, status ("BELOW_BENCHMARK" | "MEETS_BENCHMARK" | "EXCEEDS_BENCHMARK")
            - < 1.5: BELOW, 1.5-2.9: MEETS, >= 3.0: EXCEEDS
          * repeatPurchaseRate: 실제 값, status
            - < 15%%: BELOW, 15-24%%: MEETS, >= 25%%: EXCEEDS
          * fillRate: 실제 값, status (슬롯 채움률)
            - < 50%%: BELOW, 50-79%%: MEETS, >= 80%%: EXCEEDS

        # 출력 형식
        **반드시 유효한 JSON 형식으로만 응답하세요. 다른 텍스트 포함 금지.**

        ```json
        {
          "overallAssessment": "SUCCESS",
          "keyFindings": [
            {
              "metric": "...",
              "value": "...",
              "interpretation": "..."
            }
          ],
          "rootCauses": [
            {
              "issue": "...",
              "evidence": "...",
              "impact": "HIGH"
            }
          ],
          "recommendations": [
            {
              "action": "...",
              "rationale": "...",
              "priority": 1
            }
          ],
          "benchmarks": {
            "ltvCacRatio": 1.8,
            "ltvCacStatus": "BELOW_BENCHMARK",
            "repeatPurchaseRate": 12.5,
            "repeatStatus": "BELOW_BENCHMARK",
            "fillRate": 85.0,
            "fillStatus": "EXCEEDS_BENCHMARK"
          }
        }
        ```

        # 주의사항
        - 데이터에 없는 정보는 추측하지 말 것
        - 모든 해석은 제공된 데이터에 기반할 것
        - 한국어로 작성 (JSON 키는 영문 유지)
        - 숫자는 천단위 쉼표 포함 (예: 1,000,000)
        """.formatted(dataContext);
}
```

### 2.5 응답 파싱

```java
private CampaignInsightResponse parseInsightResponse(
    String geminiResponse,
    Object dashboardData,
    Object cohortData
) {
    try {
        // Gemini가 ```json ... ``` 형태로 반환할 수 있으므로 정리
        String jsonContent = geminiResponse;
        if (jsonContent.contains("```json")) {
            jsonContent = jsonContent.substring(
                jsonContent.indexOf("```json") + 7,
                jsonContent.lastIndexOf("```")
            ).trim();
        } else if (jsonContent.contains("```")) {
            jsonContent = jsonContent.substring(
                jsonContent.indexOf("```") + 3,
                jsonContent.lastIndexOf("```")
            ).trim();
        }

        // Jackson으로 파싱
        CampaignInsightResponse response = objectMapper.readValue(
            jsonContent,
            CampaignInsightResponse.class
        );

        // rawData 추가 (디버깅용)
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("dashboard", dashboardData);
        rawData.put("cohort", cohortData);
        // response에 rawData 세팅 (필요시 setter 추가)

        return response;

    } catch (Exception e) {
        log.error("Failed to parse Gemini insight response", e);

        // 파싱 실패 시 기본 응답 반환
        return createFallbackInsightResponse(geminiResponse);
    }
}

private CampaignInsightResponse createFallbackInsightResponse(String rawResponse) {
    // 파싱 실패 시 기본 구조 반환
    CampaignInsightResponse fallback = new CampaignInsightResponse();
    fallback.setOverallAssessment("UNKNOWN");
    fallback.setKeyFindings(List.of(
        new CampaignInsightResponse.KeyFinding(
            "분석 오류",
            "N/A",
            "인사이트 생성 중 오류 발생: " + rawResponse.substring(0, Math.min(100, rawResponse.length()))
        )
    ));
    fallback.setRootCauses(List.of());
    fallback.setRecommendations(List.of());
    fallback.setBenchmarks(new CampaignInsightResponse.BenchmarkComparison(
        0.0, "UNKNOWN", 0.0, "UNKNOWN", 0.0, "UNKNOWN"
    ));
    return fallback;
}
```

---

## 3. API 엔드포인트 추가

### 3.1 컨트롤러 확장

```java
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LLMController {

    private final LLMQueryService llmQueryService;

    // 기존 엔드포인트...

    /**
     * 캠페인 인사이트 자동 생성
     */
    @GetMapping("/insights/campaign/{campaignId}")
    public ResponseEntity<CampaignInsightResponse> getCampaignInsights(
        @PathVariable Long campaignId
    ) {
        CampaignInsightResponse insights =
            llmQueryService.generateCampaignInsights(campaignId);
        return ResponseEntity.ok(insights);
    }

    /**
     * Activity 인사이트 자동 생성
     */
    @GetMapping("/insights/activity/{activityId}")
    public ResponseEntity<CampaignInsightResponse> getActivityInsights(
        @PathVariable Long activityId
    ) {
        CampaignInsightResponse insights =
            llmQueryService.generateActivityInsights(activityId);
        return ResponseEntity.ok(insights);
    }
}
```

### 3.2 API 사용 예시

**요청**:
```http
GET /api/v1/llm/insights/campaign/123
```

**응답**:
```json
{
  "overallAssessment": "FAILURE",
  "keyFindings": [
    {
      "metric": "LTV/CAC 비율",
      "value": "1.8",
      "interpretation": "건강한 임계값 3.0보다 낮아 고객 획득 비용 대비 수익이 부족함"
    },
    {
      "metric": "재구매율",
      "value": "12.5%",
      "interpretation": "업계 평균 25% 대비 절반 수준으로 고객 유지 실패"
    },
    {
      "metric": "슬롯 채움률",
      "value": "85%",
      "interpretation": "목표 대비 우수한 초기 참여율"
    }
  ],
  "rootCauses": [
    {
      "issue": "높은 첫 구매 후 이탈률",
      "evidence": "87.5%의 고객이 첫 구매 후 재구매 없음",
      "impact": "HIGH"
    },
    {
      "issue": "과도한 마케팅 비용",
      "evidence": "CAC 55,000원으로 평균 LTV 99,000원 대비 높음",
      "impact": "HIGH"
    }
  ],
  "recommendations": [
    {
      "action": "첫 구매 후 7일 내 20% 할인 쿠폰 자동 발송",
      "rationale": "초기 재구매 전환율 향상을 통한 LTV 증대 필요. 업계 사례상 30% 재구매율 향상 기대",
      "priority": 1
    },
    {
      "action": "고가 상품 중심 캠페인 구성",
      "rationale": "현재 평균 객단가 35,000원으로 낮음. 단가 상승을 통한 LTV 개선 필요",
      "priority": 2
    },
    {
      "action": "타겟 오디언스 세분화 및 CAC 절감",
      "rationale": "광범위한 타겟팅으로 비효율 발생. 고가치 고객 세그먼트 집중 필요",
      "priority": 3
    }
  ],
  "benchmarks": {
    "ltvCacRatio": 1.8,
    "ltvCacStatus": "BELOW_BENCHMARK",
    "repeatPurchaseRate": 12.5,
    "repeatStatus": "BELOW_BENCHMARK",
    "fillRate": 85.0,
    "fillStatus": "EXCEEDS_BENCHMARK"
  },
  "rawData": {
    "dashboard": { ... },
    "cohort": { ... }
  }
}
```

---

## 4. 캐싱 전략

### 4.1 Redis 캐싱 추가

인사이트 생성은 비용이 발생하므로 적극적인 캐싱 필요:

```java
@Service
@RequiredArgsConstructor
public class CachedLLMInsightService {

    private final LLMQueryService llmQueryService;
    private final RedisTemplate<String, CampaignInsightResponse> redisTemplate;

    private static final Duration CACHE_TTL = Duration.ofHours(6); // 6시간

    public CampaignInsightResponse getCampaignInsights(Long campaignId) {
        String cacheKey = "insight:campaign:" + campaignId;

        // 캐시 확인
        CampaignInsightResponse cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache hit for campaign insights: {}", campaignId);
            return cached;
        }

        // 캐시 미스 - LLM 호출
        log.info("Cache miss for campaign insights: {}, calling Gemini", campaignId);
        CampaignInsightResponse insights = llmQueryService.generateCampaignInsights(campaignId);

        // 캐시 저장
        redisTemplate.opsForValue().set(cacheKey, insights, CACHE_TTL);

        return insights;
    }

    public CampaignInsightResponse getActivityInsights(Long activityId) {
        String cacheKey = "insight:activity:" + activityId;

        CampaignInsightResponse cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache hit for activity insights: {}", activityId);
            return cached;
        }

        log.info("Cache miss for activity insights: {}, calling Gemini", activityId);
        CampaignInsightResponse insights = llmQueryService.generateActivityInsights(activityId);
        redisTemplate.opsForValue().set(cacheKey, insights, CACHE_TTL);

        return insights;
    }

    /**
     * 캠페인 상태 변경 시 캐시 무효화
     */
    public void invalidateCampaignCache(Long campaignId) {
        redisTemplate.delete("insight:campaign:" + campaignId);
    }

    public void invalidateActivityCache(Long activityId) {
        redisTemplate.delete("insight:activity:" + activityId);
    }
}
```

---

## 5. UI 통합

### 5.1 대시보드에 인사이트 탭 추가

```html
<!-- dashboard.html -->
<div class="insights-section">
    <h3>🤖 AI 인사이트</h3>
    <button id="generateInsightsBtn" class="btn btn-primary">
        인사이트 생성
    </button>

    <div id="insightsContainer" style="display:none;">
        <!-- 전체 평가 -->
        <div class="assessment-badge">
            <span id="assessmentStatus"></span>
        </div>

        <!-- 핵심 발견사항 -->
        <div class="key-findings">
            <h4>핵심 발견사항</h4>
            <ul id="findingsList"></ul>
        </div>

        <!-- 권장사항 -->
        <div class="recommendations">
            <h4>권장사항</h4>
            <ol id="recommendationsList"></ol>
        </div>

        <!-- 벤치마크 -->
        <div class="benchmarks">
            <h4>벤치마크 비교</h4>
            <div id="benchmarksChart"></div>
        </div>
    </div>
</div>

<script>
document.getElementById('generateInsightsBtn').addEventListener('click', async () => {
    const campaignId = /* 현재 캠페인 ID */;
    const btn = document.getElementById('generateInsightsBtn');

    btn.disabled = true;
    btn.textContent = '생성 중...';

    try {
        const response = await fetch(`/api/v1/llm/insights/campaign/${campaignId}`);
        const insights = await response.json();

        // 전체 평가 표시
        const statusBadge = document.getElementById('assessmentStatus');
        statusBadge.textContent = insights.overallAssessment;
        statusBadge.className = `badge badge-${getStatusColor(insights.overallAssessment)}`;

        // 핵심 발견사항 렌더링
        const findingsList = document.getElementById('findingsList');
        findingsList.innerHTML = insights.keyFindings.map(f => `
            <li>
                <strong>${f.metric}:</strong> ${f.value}<br>
                <span class="text-muted">${f.interpretation}</span>
            </li>
        `).join('');

        // 권장사항 렌더링
        const recommendationsList = document.getElementById('recommendationsList');
        recommendationsList.innerHTML = insights.recommendations.map(r => `
            <li class="priority-${r.priority}">
                <strong>${r.action}</strong><br>
                <span class="text-muted">${r.rationale}</span>
                <span class="badge badge-priority">우선순위 ${r.priority}</span>
            </li>
        `).join('');

        // 컨테이너 표시
        document.getElementById('insightsContainer').style.display = 'block';

    } catch (error) {
        alert('인사이트 생성 실패: ' + error.message);
    } finally {
        btn.disabled = false;
        btn.textContent = '인사이트 재생성';
    }
});

function getStatusColor(assessment) {
    switch(assessment) {
        case 'SUCCESS': return 'success';
        case 'MIXED': return 'warning';
        case 'FAILURE': return 'danger';
        default: return 'secondary';
    }
}
</script>
```

---

## 6. 구현 체크리스트

### Phase 1: 백엔드 구현 (1-2일)
- [ ] `CampaignInsightResponse` DTO 생성 (KeyFinding, RootCause, Recommendation, BenchmarkComparison 포함)
- [ ] `LLMQueryService` 인터페이스에 메서드 추가
  - [ ] `generateCampaignInsights(Long campaignId)`
  - [ ] `generateActivityInsights(Long activityId)`
- [ ] `GeminiLLMQueryService`에 구현
  - [ ] 인사이트 프롬프트 작성 (`buildInsightPrompt()`)
  - [ ] JSON 응답 파싱 (`parseInsightResponse()`)
  - [ ] 폴백 핸들링 (`createFallbackInsightResponse()`)
- [ ] `MockLLMQueryService`에 더미 구현 (테스트용)
- [ ] 단위 테스트 작성

### Phase 2: API 및 캐싱 (1일)
- [ ] LLM 컨트롤러에 엔드포인트 추가
  - [ ] `GET /api/v1/llm/insights/campaign/{campaignId}`
  - [ ] `GET /api/v1/llm/insights/activity/{activityId}`
- [ ] Redis 캐싱 레이어 추가 (`CachedLLMInsightService`)
- [ ] 통합 테스트 작성

### Phase 3: UI 통합 (1-2일)
- [ ] 대시보드에 "AI 인사이트" 섹션 추가
- [ ] "인사이트 생성" 버튼 구현
- [ ] 인사이트 결과 렌더링 (전체 평가, 발견사항, 권장사항, 벤치마크)
- [ ] 로딩 상태 및 에러 핸들링
- [ ] CSS 스타일링

### Phase 4: 프롬프트 튜닝 및 검증 (1일)
- [ ] 실제 캠페인 데이터로 테스트 (최소 5개 캠페인)
- [ ] 프롬프트 정교화 (벤치마크 임계값 조정, 출력 품질 개선)
- [ ] 에지 케이스 처리 (데이터 없음, API 실패 등)
- [ ] 성능 테스트 (응답 시간, 캐시 히트율)

---

## 7. 예상 비용

### Gemini 2.0 Flash 가격
- **입력**: $0.075 per 1M tokens (무료 티어: 1,500 RPD)
- **출력**: $0.30 per 1M tokens

### 예상 토큰 사용량
- **입력 토큰**: ~3,000 (대시보드 + Cohort 데이터 + 프롬프트)
- **출력 토큰**: ~1,000 (구조화된 JSON 인사이트)
- **요청당 비용**: $(3,000 × 0.075 + 1,000 × 0.30) / 1,000,000 = **$0.00053** (~₩0.7)

### 월간 예상 비용
- **시나리오**: 100개 캠페인, 각 캠페인당 하루 1회 인사이트 생성
- **총 요청**: 100 × 30 = 3,000 requests/month
- **월 비용**: 3,000 × $0.00053 = **$1.59** (~₩2,100)
- **6시간 캐싱 적용 시**: 실제 비용 ~**$0.40** (~₩530)

**결론**: 매우 저렴함 (무료 티어 내에서 충분히 가능)

---

## 8. 리스크 및 대응

| 리스크 | 영향도 | 대응 방안 |
|--------|--------|-----------|
| **Gemini API 장애** | 중 | 6시간 캐시로 최근 인사이트 제공, 에러 메시지 표시 |
| **JSON 파싱 실패** | 중 | 폴백 응답 반환, 프롬프트에 JSON 형식 명시 강화 |
| **부정확한 인사이트** | 고 | 프롬프트 튜닝, 벤치마크 기준 명확화, 실제 데이터로 검증 |
| **느린 응답 시간** | 저 | 비동기 처리, 프론트엔드 로딩 UI, 적극적 캐싱 |

---

## 9. 개선 방향 (향후)

### 단기 (1-2개월)
- 인사이트 히스토리 저장 (DB 테이블 추가)
- 인사이트 비교 기능 (시간대별 변화 추적)
- 사용자 피드백 수집 (👍/👎 버튼)

### 중기 (3-6개월)
- 멀티 캠페인 비교 인사이트
- 예측 인사이트 (캠페인 시작 전 예상 성과)
- Elasticsearch 행동 데이터 추가 분석
- 자동 인사이트 리포트 이메일 발송

### 장기 (6개월+)
- 커스텀 벤치마크 설정
- A/B 테스트 권장사항 자동 생성
- 실시간 인사이트 (마일스톤 도달 시 자동 생성)
- 다국어 지원 (영어, 일본어 등)

---

## 요약

### 핵심 포인트
✅ **기존 Gemini 통합 활용** - 새로운 API 연동 불필요
✅ **데이터 수집 로직 재사용** - DashboardService, CohortAnalysisService 그대로 활용
✅ **간단한 확장** - 인터페이스 메서드 2개, DTO 1개, 프롬프트 1개 추가
✅ **저비용** - 월 ~₩500 (캐싱 적용 시)
✅ **빠른 구현** - 3-5일 내 완료 가능

### 다음 단계
1. `CampaignInsightResponse` DTO 생성부터 시작
2. `GeminiLLMQueryService`에 메서드 추가
3. 프롬프트 작성 및 테스트
4. API 엔드포인트 추가
5. 대시보드 UI 통합

**구현을 시작할까요?**
