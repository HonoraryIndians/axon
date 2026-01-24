📋 Refactoring Plan: Robust FCFS Payment System

Phase 1. Core Service 설정 및 인프라 구성 (Configuration)
가장 먼저 데이터를 받아낼 그릇(Batch Listener)과 저장소 설정(JDBC)을 최적화합니다.

1. Kafka Batch Factory 생성 (`KafkaConfig.java`)
    * batchKafkaListenerContainerFactory 빈 등록.
    * batchListener(true) 활성화.
    * max.poll.records: 50 (장애 복구 및 로컬 환경 최적화).
    * fetch.max.wait.ms: 500 (0.5초 대기 후 즉시 처리).
2. JDBC Bulk Insert 최적화 (`application.yml`)
    * JPA가 내부적으로 INSERT를 묶어서 보내도록 hibernate.jdbc.batch_size: 50 설정 추가.

Phase 2. 데이터 영속화 계층 구현 (Persistence Layer)
Kafka에서 넘어온 데이터를 DB에 안전하게 저장하기 위한 로직입니다.

3. Entity 및 Repository 점검 (`CampaignEntry.java`)
    * CampaignEntry 엔티티 확인 (없으면 생성).
    * 필수 필드: userId, campaignId, createdAt (발급 시간 - Lazy TTL용).
    * CampaignEntryRepository: findByUserIdAndCampaignId 메서드 존재 여부 확인.
4. Batch Consumer 구현 (`CampaignSuccessConsumer.java`)
    * 기존 단건 리스너를 Batch 리스너(`List<Event>`)로 변경.
    * Dual-Try Strategy (이중 안전장치) 적용:
        * 1차: repository.saveAll(events) (Bulk Insert).
        * 2차 (Fallback): 실패(DataIntegrityViolation 등) 시 for문으로 개별 `save()` 수행.
        * 3차 (DLQ): 개별 저장도 실패한 데이터는 에러 로그/DLQ 처리.

Phase 3. 결제 검증 로직 구현 (Business Logic)
결제 진입 시 1차 토큰과 DB를 교차 검증하고, 악성 요청을 방어합니다.

5. Payment Service 검증 로직 구현 (`PaymentService.java`)
    * Step 1 (Redis): 1차 토큰 조회 (가장 빠름). 유효하면 Pass.
    * Step 2 (DB - Fallback): Redis에 없으면 CampaignEntryRepository 조회.
    * Step 3 (Lazy TTL): DB 데이터의 createdAt과 현재 시간 비교. (예: 30분 경과 시 예외 발생).
    * Step 4 (DB Protection): DB에도 데이터가 없는 경우(악성 요청), Local Cache(Caffeine/Map)에 잠시 ID를 기록하여 짧은 시간 동안 DB 재조회 차단.

Phase 4. Entry Service 경량화 (Cleanup)
이제 필요 없어진 2차 토큰 로직을 제거하고 Kafka 발행을 확실히 합니다.

6. 2차 토큰 로직 제거
    * 기존 결제용 토큰 발급/검증 코드 삭제 (복잡도 제거).
7. Producer 확인
    * 선착순 성공(INCR 성공) 직후 Kafka로 CampaignSuccessEvent가 올바르게 발행되는지 확인.

  ---

🚀 진행 시작

가장 기초가 되는 Phase 1: Kafka Config (Core Service)부터 수정하겠습니다.
준비되셨나요?

(동의하시면 core-service의 KafkaConfig.java 파일을 열고 수정을 시작합니다.)