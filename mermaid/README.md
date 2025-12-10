# Axon CDP - 공모전 발표용 Mermaid 다이어그램

IntelliJ Mermaid Plugin 또는 온라인 에디터에서 시각화할 수 있는 다이어그램 모음입니다.

## 📋 다이어그램 목록 (표 구조)

| 타입 | 파일명 | 설명 |
|------|--------|------|
| **Flowchart** | `03-fcfs-payment-flowchart.mmd` | FCFS + 결제 전체 프로세스 |
| **Flowchart** | `flowchart-dashboard-analysis.mmd` | 분석 대시보드 접근 및 드릴다운 |
| **Sequence** | `sequence-fcfs-batch.mmd` | FCFS 배치 (Kafka Consumer 마이크로 배칭) |
| **Sequence** | `sequence-cohort-batch.mmd` | 코호트 LTV 배치 (Spring Scheduler) |
| **Sequence** | `04-js-snippet-pipeline.mmd` | JS 스니펫 수집 파이프라인 |
| **Sequence** | `sequence-llm.mmd` | LLM 챗봇 통합 (Gemini AI) |
| **Graph** | `01-overall-architecture.mmd` | 전체 아키텍처 구조 |
| **Graph** | `graph-dashboard-pipeline.mmd` | 대시보드 데이터 파이프라인 |
| **Use-case** | `02-usecase-diagram.mmd` | 사용자/관리자 기능 |

## 🎯 다이어그램별 상세 설명

### 1. Flowchart: FCFS + 결제 로직
**파일**: `03-fcfs-payment-flowchart.mmd`

**포함 내용**:
- 사용자 FCFS 참여 전체 플로우
- Fast/Heavy Validation 단계
- Redis 원자성 보장 (SADD → INCR → 한도 체크)
- 이중 토큰 발급 (결정론적 토큰 + HMAC 서명)
- 재결제 시나리오
- Kafka 비동기 처리
- Core Service 배치 처리 및 재고 차감

**핵심 키워드**:
- Redis 원자성
- 멱등성 보장
- 이중 토큰 검증
- Kafka 버퍼링
- 데이터 유실 방지

---

### 2. Flowchart: 분석 대시보드
**파일**: `flowchart-dashboard-analysis.mmd`

**포함 내용**:
- 4단계 트리 구조 (Global → Campaign → Activity → Cohort)
- 각 레벨별 KPI 계산
- 드릴다운 탐색
- CSV 내보내기
- LLM 챗봇 연동

**핵심 키워드**:
- 계층적 대시보드
- ROI/ROAS 분석
- LTV/CAC 비율
- 실시간 재고 모니터링

---

### 3. Sequence: FCFS 배치 처리
**파일**: `sequence-fcfs-batch.mmd`

**포함 내용**:
- Kafka Consumer 마이크로 배칭 패턴
- 버퍼링 조건 (20개 또는 100ms)
- 중복 제거 로직 (activityId:userId)
- Bulk Upsert 전략
- Graceful Shutdown

**핵심 키워드**:
- 마이크로 배칭
- 서버 부하 분산
- 트랜잭션 청크
- 메모리 효율

---

### 4. Sequence: 코호트 LTV 배치
**파일**: `sequence-cohort-batch.mmd`

**포함 내용**:
- Spring Scheduler (매월 1일 03:00)
- 증분 업데이트 vs 전체 계산
- 12개월 추적
- 재구매율/LTV/CAC 계산
- 배치 버퍼링 (50개씩)

**핵심 키워드**:
- 코호트 분석
- 증분 계산
- LTV/CAC 비율
- 손익분기점 분석

---

### 5. Sequence: JS 스니펫 수집 파이프라인
**파일**: `04-js-snippet-pipeline.mmd`

**포함 내용**:
- BehaviorTracker.js 초기화
- PAGE_VIEW/CLICK 이벤트 수집
- History API 모니터링
- Entry Service 전송
- Kafka → Elasticsearch 파이프라인

**핵심 키워드**:
- 행동 추적
- 이벤트 위임
- 쿨다운 메커니즘
- 실시간 집계

---

### 6. Sequence: LLM 챗봇
**파일**: `sequence-llm.mmd`

**포함 내용**:
- 자연어 쿼리 처리
- 메트릭 데이터 수집 (병렬)
- Gemini API 호출
- 프롬프트 구조
- AI 인사이트 생성

**핵심 키워드**:
- Gemini AI
- 자연어 쿼리
- 컨텍스트 빌딩
- 마케팅 인사이트

---

### 7. Graph: 전체 아키텍처
**파일**: `01-overall-architecture.mmd`

**포함 내용**:
- Core-Service (8080) vs Entry-Service (8081) 분리
- Kafka 메시징
- Redis FCFS 로직
- Elasticsearch 데이터 파이프라인
- Grafana 모니터링

**핵심 키워드**:
- 부하 분산
- 마이크로서비스
- 이벤트 드리븐
- 장애 격리

---

### 8. Graph: 대시보드 데이터 파이프라인
**파일**: `graph-dashboard-pipeline.mmd`

**포함 내용**:
- 3개 데이터 소스 (ES, MySQL, Redis)
- 계층별 메트릭 계산
- Chart.js 시각화

**핵심 키워드**:
- 다중 데이터 소스
- 실시간 + 배치
- 집계 최적화

---

### 9. Use-case: 사용자 및 관리자
**파일**: `02-usecase-diagram.mmd`

**포함 내용**:
- 일반 사용자: 회원가입, FCFS 참여, 결제
- 마케터: 4단계 대시보드, LLM 챗봇, 캠페인 관리
- 개발자: 성능 모니터링, Grafana, 이벤트 관리

**핵심 키워드**:
- 역할 기반 기능
- 트리 구조 대시보드
- AI 인사이트

---

## 🚀 사용 방법

### IntelliJ Mermaid Plugin
1. IntelliJ IDEA에서 `Settings → Plugins` 검색: "Mermaid"
2. 설치 후 `.mmd` 파일 오픈
3. 오른쪽 Preview 탭에서 시각화

### 온라인 에디터
- [Mermaid Live Editor](https://mermaid.live/)
- 파일 내용 복사 → 붙여넣기 → 자동 렌더링

### VS Code
- Extension 설치: "Markdown Preview Mermaid Support"
- `.mmd` 파일을 마크다운 코드 블록으로 감싸기:
  ````markdown
  ```mermaid
  [파일 내용]
  ```
  ````

---

## 📊 발표 시 활용 전략

### 슬라이드 1: 전체 아키텍처
- **파일**: `01-overall-architecture.mmd`
- **메시지**: Core/Entry 분리로 부하 분산 및 확장성 확보

### 슬라이드 2: FCFS 핵심 로직
- **파일**: `03-fcfs-payment-flowchart.mmd`
- **메시지**: Redis 원자성 + 이중 토큰으로 정확성과 보안 보장

### 슬라이드 3: 배치 처리 효율성
- **파일**: `sequence-fcfs-batch.mmd` + `sequence-cohort-batch.mmd`
- **메시지**: 마이크로 배칭으로 서버 부하 최소화

### 슬라이드 4: 행동 추적 파이프라인
- **파일**: `04-js-snippet-pipeline.mmd`
- **메시지**: 실시간 이벤트 수집 → Kafka → Elasticsearch

### 슬라이드 5: 마케터 대시보드
- **파일**: `flowchart-dashboard-analysis.mmd` + `graph-dashboard-pipeline.mmd`
- **메시지**: 4단계 트리 구조로 캠페인부터 코호트까지 드릴다운

### 슬라이드 6: AI 인사이트
- **파일**: `sequence-llm.mmd`
- **메시지**: Gemini AI로 자연어 쿼리 → 마케팅 인사이트 제공

---

## 🎨 색상 범례

| 색상 | 의미 |
|------|------|
| 파란색 (`#e1f5ff`) | Entry Service (빠른 응답) |
| 빨간색 (`#ffebee`) | FCFS 핵심 로직 (원자성 보장) |
| 주황색 (`#fff3e0`) | Kafka 메시징 (비동기 처리) |
| 초록색 (`#e8f5e9`) | Dashboard (분석) |
| 보라색 (`#f3e5f5`) | LLM (AI) |
| 노란색 (`#fff9c4`) | Cohort (LTV 분석) |

---

## 📝 주의사항

- 모든 다이어그램에서 이모티콘 제거 완료 (가시성 개선)
- 텍스트 길이 최적화 (박스 내 여백 확보)
- 한글 + 영문 병기로 국제 심사위원 대응

---

## 🔗 관련 문서

- 프로젝트 루트: `/Users/dem/Project/Axon/`
- 코드베이스: `core-service/`, `entry-service/`
- 설계 문서: `docs/`
- CLAUDE.md: 프로젝트 개요 및 아키텍처 설명
