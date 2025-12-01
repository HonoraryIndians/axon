# 📅 남은 2주 개발 및 개선 계획 (Backlog)

> **작성일**: 2025-11-28
> **목표**: 시스템 안정화, 대시보드 고도화, 그리고 포트폴리오 퀄리티 향상

---

## 1. 📊 대시보드 고도화 (Priority: High)

### A. Campaign Level 성능 최적화 (Refactoring)
*   **현재 상황 (As-Is):** 캠페인 대시보드 조회 시, 소속된 `Activity` 개수(N)만큼 ES와 DB를 반복 조회하는 **N+1 문제** 존재. (초기 구현의 한계)
*   **개선 목표 (To-Be):** `campaignId`를 Elasticsearch 인덱스에 직접 적재하여, **단 1회의 Aggregation 쿼리**로 캠페인 전체 통계를 집계.
*   **Action Items:**
    1.  `Entry-Service`: Kafka 이벤트 발행 시 `campaignId` 필드 추가 (DTO 수정).
    2.  `Core-Service`: ES 인덱싱 로직 확인.
    3.  `DashboardService`: 루프 로직 제거 및 `terms aggregation` 기반 단일 쿼리로 변경.
    4.  **성과 측정:** 개선 전/후 API 응답 속도(Latency) 비교하여 포트폴리오에 기재.

### B. CDP 심층 분석 지표 추가
*   **Click Rate (Engagement):** 단순 방문/구매뿐만 아니라, `Visit` -> `Click` 전환율을 보여주어 이탈 지점 분석 강화.
*   **Device Breakdown:** `User-Agent` 문자열을 파싱(Mobile/Desktop/Tablet)하여 원형 차트(Pie Chart)로 시각화.
*   **New vs Returning (재방문 분석):**
    *   `Entry-Service`에서 이벤트 발행 시 유저의 가입일/방문이력을 조회하여 `isNewUser: true/false` 마킹.
    *   ES에서 해당 필드로 필터링하여 신규 유입 퍼포먼스 분석.

---

## 2. ⚡ 성능 및 안정성 검증 (Priority: Medium)

### A. 대규모 부하 테스트 (Load Testing)
*   **도구:** k6 (Javascript 기반)
*   **시나리오:**
    *   **Scenario 1 (Spike):** 선착순 오픈 직후 1초에 10,000명 동시 요청.
    *   **Scenario 2 (Sustained):** 5분간 지속적인 트래픽 유입 시 시스템 리소스(CPU/Memory) 변화.
*   **목표:** `Redisson` 분산 락이 100% 동작하여 **Over-booking이 0건**임을 증명.

### B. 장애 복구 훈련 (Chaos Engineering)
*   **시나리오:**
    *   Redis Master 노드 강제 종료 -> Slave 승격 확인.
    *   Kafka Broker 1대 종료 -> 데이터 유실 없이 처리되는지 확인.
*   **목표:** **SPOF(Single Point of Failure) 없음**을 증명.

---

## 3. 🤖 확장 기능 (Wishlist / Low)

### A. LLM 기반 자연어 쿼리 시스템
*   **기능:** "지난주 아이폰 캠페인 성과 어때?"라고 물으면 요약된 텍스트나 차트를 보여줌.
*   **구현:** OpenAI API (or Claude) 프롬프트 엔지니어링 + `DashboardService` API 연결.
*   **전략:** 실제 구현이 어렵다면 아키텍처 설계와 프롬프트 예시만이라도 문서화하여 **"AI 도입 가능성"** 어필.

---

## 🗓️ 주차별 실행 계획

| 주차 | 주요 작업 | 담당 |
| :--- | :--- | :--- |
| **이번 주 (잔여)** | `Click Rate`, `Device Breakdown` 지표 추가 (Frontend/Backend) | Dev A/B |
| **다음 주 (1주차)** | 부하 테스트(k6) 수행 및 리포트 작성, `campaignId` 최적화 작업 | Dev A |
| **다다음 주 (2주차)** | 장애 복구 훈련, 최종 포트폴리오 문서(README, PPT) 정리, LLM 기획 | Dev B |
