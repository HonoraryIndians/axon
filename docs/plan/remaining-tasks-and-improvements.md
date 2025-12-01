# 📅 남은 2주 개발 및 개선 계획 (Backlog)

> **작성일**: 2025-11-28
> **목표**: 시스템 안정화, 대시보드 고도화, 그리고 포트폴리오 퀄리티 향상

---

## 1. 📊 대시보드 고도화 (Priority: High)

### A. Campaign Level 성능 최적화 (Refactoring) ✅ **완료**
*   **현재 상황 (As-Is):** 캠페인 대시보드 조회 시, 소속된 `Activity` 개수(N)만큼 ES와 DB를 반복 조회하는 **N+1 문제** 존재. (초기 구현의 한계)
*   **개선 목표 (To-Be):** `campaignId`를 Elasticsearch 인덱스에 직접 적재하여, **단 1회의 Aggregation 쿼리**로 캠페인 전체 통계를 집계.
*   **Action Items:**
    - [x] `Entry-Service`: Kafka 이벤트 발행 시 `campaignId` 필드 추가 (DTO 수정)
    - [x] `Core-Service`: ES 인덱싱 로직 확인
    - [x] `DashboardService`: 루프 로직 제거 및 `terms aggregation` 기반 단일 쿼리로 변경
    - [x] **성과 측정:** 개선 전/후 API 응답 속도(Latency) 비교하여 포트폴리오에 기재

### B. CDP 심층 분석 지표 추가
*   **Click Rate (Engagement):** ✅ **완료**
    - [x] CTR (Click-Through Rate) = `engagementRate` 구현
    - [x] CVR (Conversion Rate) 구현
    - [x] Dashboard UI에 표시 중

*   **Device Breakdown:** ❌ **미완료**
    - [ ] `User-Agent` 문자열 파싱 (Mobile/Desktop/Tablet)
    - [ ] 원형 차트(Pie Chart) 시각화 구현

*   **New vs Returning (재방문 분석):** ❌ **미완료**
    - [ ] `Entry-Service`: 이벤트 발행 시 유저 가입일/방문이력 조회
    - [ ] `isNewUser: true/false` 필드 추가
    - [ ] ES 필터링 및 대시보드 UI 구현

---

## 2. ⚡ 성능 및 안정성 검증 (Priority: Medium)

### A. 대규모 부하 테스트 (Load Testing)
*   **도구:** k6 (Javascript 기반)
*   **시나리오:**
    - [ ] **Scenario 1 (Spike):** 선착순 오픈 직후 1초에 10,000명 동시 요청
    - [ ] **Scenario 2 (Sustained):** 5분간 지속적인 트래픽 유입 시 시스템 리소스(CPU/Memory) 변화
*   **목표:** `Redisson` 분산 락이 100% 동작하여 **Over-booking이 0건**임을 증명
    - [ ] k6 스크립트 작성 및 실행
    - [ ] 결과 리포트 작성 (Grafana 대시보드 캡처)

### B. 장애 복구 훈련 (Chaos Engineering)
*   **시나리오:**
    - [ ] Redis Master 노드 강제 종료 → Slave 승격 확인
    - [ ] Kafka Broker 1대 종료 → 데이터 유실 없이 처리되는지 확인
*   **목표:** **SPOF(Single Point of Failure) 없음**을 증명
    - [ ] 장애 시나리오 테스트 수행
    - [ ] 복구 절차 문서화

---

## 3. 🧪 Activity 시뮬레이션 기능 (Priority: Medium-High)

> **📄 상세 계획**: [`docs/simulation-feature-plan.md`](../simulation-feature-plan.md)

### 개요
마케터와 개발자가 새로운 Activity를 등록할 때, 실제 트래픽 없이 대시보드 데이터를 미리 시뮬레이션하여 확인할 수 있는 기능.

### 핵심 기능
*   **UI**: Admin Dashboard에 "🧪 시뮬레이션 실행" 버튼 추가
*   **파라미터 설정**: 방문자 수, 전환율, 시간 범위 조정 가능
*   **실시간 진행 상황**: SSE를 통한 Progress 스트리밍
*   **안전장치**:
    - DRAFT/TEST 상태 Activity만 시뮬레이션 가능
    - ADMIN/MANAGER 권한 필요
    - Redis 분산 락으로 동시 실행 방지
    - 데이터 격리 (user_id >= 1000)

### 기술 스택
*   **Backend**: Spring @Async + SSE (Server-Sent Events)
*   **Frontend**: JavaScript EventSource API + Modal UI
*   **데이터 처리**: 기존 스크립트 로직을 Java로 포팅
*   **진행 관리**: Redis + MySQL (simulation_jobs 테이블)

### 예상 일정
| Phase | 작업 | 기간 |
|-------|------|------|
| Phase 1 | Backend API 구현 (Controller, Service, Executor) | 2-3일 |
| Phase 2 | Frontend UI 구현 (Modal, Progress Bar, SSE) | 1-2일 |
| Phase 3 | 테스트 & QA (단위/통합 테스트) | 1일 |
| **Total** | | **4-6일** |

### Action Items
1. [ ] Database Migration (simulation_jobs 테이블 생성)
2. [ ] SimulationController + Service 구현
3. [ ] SimulationExecutor (비동기 실행 로직)
4. [ ] Dashboard UI에 버튼 및 모달 추가
5. [ ] SSE 연동 및 Progress 스트리밍
6. [ ] 권한 체크 및 안전장치 구현
7. [ ] 단위/통합 테스트 작성

### 포트폴리오 어필 포인트
*   **비동기 처리**: Spring @Async를 활용한 대용량 데이터 생성
*   **실시간 통신**: SSE를 통한 사용자 경험 개선
*   **안전성**: 분산 락, 권한 체크, 데이터 격리 등 프로덕션급 안전장치
*   **재사용성**: 기존 테스트 스크립트를 API로 전환하여 활용도 증대

---

## 4. 🤖 확장 기능 (Wishlist / Low)

### A. LLM 기반 자연어 쿼리 시스템
*   **기능:** "지난주 아이폰 캠페인 성과 어때?"라고 물으면 요약된 텍스트나 차트를 보여줌.
*   **구현:** OpenAI API (or Claude) 프롬프트 엔지니어링 + `DashboardService` API 연결.
*   **전략:** 실제 구현이 어렵다면 아키텍처 설계와 프롬프트 예시만이라도 문서화하여 **"AI 도입 가능성"** 어필.

---

## 🗓️ 주차별 실행 계획

| 주차 | 주요 작업 | 담당 |
| :--- | :--- | :--- |
| **이번 주 (잔여)** | `Click Rate`, `Device Breakdown` 지표 추가 (Frontend/Backend) | Dev A/B |
| **다음 주 (1주차)** | 🧪 **시뮬레이션 기능 구현** (Phase 1-2), 부하 테스트(k6) 수행 | Dev A |
| **다음 주 (2주차)** | `campaignId` 최적화 작업, 시뮬레이션 기능 완료 (Phase 3) | Dev A |
| **다다음 주 (3주차)** | 장애 복구 훈련, 최종 포트폴리오 문서(README, PPT) 정리, LLM 기획 | Dev B |
