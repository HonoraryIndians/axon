# 4주 개발 계획 (업데이트)

| 주차 | 모듈 / 기능 | 주요 산출물 및 설명 | 담당 | 비고 |
| --- | --- | --- | --- | --- |
| **1주차** | **예약 토큰 + Mock 결제 레이어** | `ReservationTicketService`, `PaymentController`를 추가하고 Redis 기반 TTL/정리 로직을 구현해 결제 전에 선착순이 확정되도록 한다. Entry-controller는 Kafka 발행을 중단하고, Payment-controller가 토큰 소비 후에만 명령을 발행한다. | Dev A | PG 연동은 추상화, 시퀀스 다이어그램/문서 갱신 포함. |
|  | **Behavior 스니펫 고도화** | Thymeleaf 자동 주입, payload 유효성/로깅 강화, 디버그 토글 제공. Kafka 전송 스키마에 맞춰 속성을 정규화한다. | Dev B | 테스트 페이지/콘솔 로그 스크립트 포함. |
| **2주차** | **Kafka 기반 행동 로그 파이프라인** | Entry-service가 행동 이벤트를 `axon.event.raw` 토픽에 퍼블리시하고, Kafka Consumer/Connector가 bulk 로드하여 Elasticsearch `behavior-events-*` 인덱스에 적재하도록 구현한다. | Dev A | 재시도, DLQ, ES 인덱스 템플릿/ILM, 모니터링 대시 작성. |
|  | **참여 자격 캐시/검증** | Redis 유저 프로필 캐시 확장 + 미스 시 Core-service API 호출 구조 확립. 캐시 TTL·동기화 전략 문서화. | Dev B | 필터 정확도 확보 후 KPI 계산에 사용. |
| **3주차** | **대시보드 API & 쿼리 오케스트레이터** | MySQL(응모·승인·구매) + ES(행동)를 조인하는 Aggregation API 설계 및 구현. LLM이 자연어 → SQL/ES 템플릿 → 응답 스티칭을 수행하는 콘셉트 초안 작성. | Dev A | 샘플 프롬프트, 생성 쿼리, 검증 가이드 포함. |
|  | **Chart.js 대시보드 골격** | `docs/marketing-dashboard-spec.md`에 맞춰 KPI 카드, 퍼널, 세그먼트, 비용 패널 UI 구현. Mock 데이터 토글로 Week4 실데이터 연동 준비. | Dev B | 상태 관리/필터 UX, 반응형 설계 문서화. |
| **4주차** | **부하 테스트 & 성능 튜닝** | Entry + Payment confirm + Kafka + ES 인덱싱 구간에 대한 JMeter/Gatling 시나리오 작성 및 실행. Redis 키, Kafka 배치, ES Bulk 설정 튜닝 후 전/후 비교 수집. | Dev A, Dev B | KPI: TPS, P99 지연, Kafka lag, ES ingest 속도. |
|  | **문서 및 포트폴리오 패키징** | README, 아키텍처 도식, 예약 토큰/결제/행동 로그 흐름, 대시보드 화면 캡처 정리. LLM 쿼리 조합 개념자료 포함. | Dev B | 발표용 슬라이드/스크린샷/데모 영상 준비. |

> **핵심 변경 사항**
> - **Kafka 중심 행동 로그**: JS 스니펫 → Entry-service → Kafka `axon.event.raw` → ES Consumer 구조로 통일하여 대용량·재처리 요구를 충족한다.
> - **Mock 결제 계층**: 외부 PG를 흉내 내는 Payment 레이어를 도입해 “선점 → 결제 → Kafka 확정” 시나리오를 코드와 문서로 증명한다.
> - **LLM 쿼리 조합(확장 목표)**: 3주차에 자연어 프롬프트로 SQL/ES 템플릿을 생성·검증·조합하는 프로토콜을 설계하여 향후 자동화 아이디어를 보여준다.

> **실행 메모**
> - 새 컴포넌트는 모두 `docs/`에 요약 문서를 추가한다.
> - Redis 키/TTL 정책, Kafka 토픽 스키마, ES 인덱스 구조를 표준화하고 감시 지표를 정의한다.
> - 대시보드/LLM/부하 테스트 결과를 포트폴리오용 자료로 지속 수집한다.

> **Execution Reminders**
> - Every new component (`ReservationTicketService`, ES Sink, Dashboard API) must include brief docs under `docs/`.
> - Track Redis key naming/TTL policies to avoid leaks when tokens expire.
> - Keep a running “portfolio log” of screenshots, load-test charts, and architecture diagrams for the final presentation.
