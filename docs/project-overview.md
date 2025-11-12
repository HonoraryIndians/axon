# Axon CDP – Overview & Plan

## 프로젝트 개요
- 쇼핑몰 캠페인/액티비티에서 발생하는 **실시간 행동 데이터**(페이지 뷰, 클릭 등)를 JS 스니펫으로 수집해 Kafka → Elasticsearch로 적재한다.
- Entry-service에서 **선착순/필터링/토큰 기반 결제 흐름**을 처리해 FCFS 프로모션을 안정적으로 운영하고, Core-service는 명령만 받아 재고/승인을 확정한다.
- MySQL(응모/승인/구매) + ES(행동) + Redis(실시간 카운터)를 묶어 **마케팅 대시보드**(KPI, 퍼널, 세그먼트, 비용 패널)로 캠페인 성과를 시각화한다.
- 실시간 대용량 이벤트 처리에 대비해 Kafka 기반 아키텍처를 도입하고 실시간 대시보드 기능을 강조한다, 향후 LLM 오케스트레이터로 자연어 프롬프트 → SQL/ES 쿼리 생성 및 응답 조합 흐름을 구상한다.
## 아키텍처 개요
1. **Behavior Tracker**  
   - Thymeleaf 레이아웃에 자동 삽입된 JS 스니펫이 페이지 뷰/클릭을 감지하고 Entry-service의 `/api/v1/behavior/events`로 전송.
   - Entry-service는 payload 검증 후 Kafka `axon.event.raw`에 퍼블리시.
   - Kafka Connect(Elasticsearch Sink) → `behavior-events` 인덱스에 적재, 대시보드/분석에서 조회.

2. **FCFS + 결제 흐름**  
   - Entry-service가 Redis 기반 예약(중복/매진 판단) + ReservationToken 발급을 담당.
   - PaymentController가 토큰 검증/소비 후 Kafka `axon.campaign-activity.command`에 이벤트 발행 → Core-service가 재고 감소/승인 확정 수행.

3. **데이터 저장/조회**  
   - MySQL: 캠페인/응모/승인/구매 등 도메인 상태.
   - Redis: FCFS 참가자 Set, 카운터, 예약 토큰/만료.
   - Elasticsearch: 행동 로그(`behavior-events`) 및 향후 확장 인덱스.

## 향후 계획
- **예약 토큰 운영 고도화**: 만료 스케줄러, 결제 실패 시 counter 복구 로직 등 Redis 관리 강화.
- **Dashboard API & LLM 오케스트레이터**: MySQL/ES/Redis를 조합한 Aggregation API 구현, 자연어 프롬프트 → SQL/ES DSL 생성 흐름 계획.
- **차트/UX 구현**: `docs/marketing-dashboard-spec.md` 기반으로 Chart.js 레이아웃을 완성하고, 실제 API 데이터로 바인딩.
- **부하 테스트 & 문서화**: JMeter/Gatling으로 Entry→Payment→Kafka→ES 흐름을 압박하고, 최적화/포트폴리오 자료를 정리.

세부 일정과 담당자는 `docs/project-tasks.md`에 주차별로 정리되어 있으니, 에이전트/협업자는 해당 문서를 참고해 진행하면 된다.
