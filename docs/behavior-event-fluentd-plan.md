# Behavior Event → Fluentd → Elasticsearch 전환 계획

## 1. 목표 개요

현재 브라우저 스니펫이 수집한 행동 이벤트는 `entry-service` → Kafka(`axon.event.raw`)에 저장만 되고 있습니다. 향후 대시보드/마케팅 분석에서 빠르게 조회할 수 있도록 **Fluentd를 이용해 Elasticsearch(ES)**로 적재하는 파이프라인을 구축합니다. 서버 로그(표준 출력)도 동일 Fluentd 에이전트를 사용하면 ES로 함께 모아 Kibana에서 통합 모니터링이 가능합니다.

## 2. 아키텍처 개요
```
Browser Snippet
   ↓ POST /api/v1/behavior/events
Entry-Service (BehaviorEventController)
   ↓ BehaviorEventPublisher
   ↓  (Kafka → Fluentd)  ❗ 기존 방식 유지 시
OR
   ↓ BehaviorEventPublisherFluentd  ❗ 새 구현 시
   ↓ Fluentd (in Kubernetes/VM)
   ↓ Elasticsearch (Index: behavior-events-YYYY.MM.DD)
   ↓ Kibana / API / Analyzer
```

- **옵션 A**: Kafka → Fluentd → Elasticsearch
  - Kafka는 유지하고, Fluentd input plugin(kafka)로 토픽을 읽어 ES로 전송.
  - Entry-service 코드는 그대로 두고, Fluentd 설정만 추가하면 됨.
  - Reloadable 하며, 추후 다른 Consumer에도 활용 가능.

- **옵션 B**: Entry-service → Fluentd → Elasticsearch
  - `BehaviorEventPublisher`가 Kafka 대신 Fluentd forward 플러그인으로 직접 송신.
  - 단순 구조이지만, Kafka에 의존하지 않는 대신 재처리/재처리 기능이 떨어짐.

본 계획에서는 재사용성을 위해 **옵션 A** 위주로 설명하며, 필요 시 B도 검토합니다.

## 3. 데이터 스키마 & 필드 설계

ES 인덱스 구조(예시, `behavior-events-2024.11.06`):
```json
{
  "@timestamp": "2024-11-06T01:23:45.678Z",
  "eventId": 123,
  "eventName": "Entry 페이지 뷰",
  "triggerType": "PAGE_VIEW",
  "occurredAt": "2024-11-06T01:23:45.678Z",
  "userId": 42,
  "sessionId": "abc-123",
  "pageUrl": "https://shop.axon.com/entry?campaignActivityId=1",
  "referrer": "https://shop.axon.com",
  "userAgent": "Mozilla/5.0 ...",
  "properties": {
    "campaignActivityId": 1,
    "productId": 1
  },
  "context": {
    "ip": "1.2.3.4",
    "cookies": "..." // 필요 시
  }
}
```

- `@timestamp`: Fluentd가 추가하거나 `occurredAt`을 복사. Kibana 시각화 기준으로 사용.
- `properties`: JSON 객체로 저장되며, Kibana에서 key별로 분리하거나 nested로 맵핑 가능.
- `userAgent`, `ip` 등은 Fluentd filter를 통해 추가 수집도 가능.

## 4. 구현 상세 흐름

### 4.1 Fluentd 설정 (옵션 A: Kafka → ES)

1. Fluentd 설치 (Docker 컨테이너 혹은 호스트 서비스)
2. `fluent.conf` 예시
   ```conf
   <source>
     @type kafka
     brokers localhost:9092
     format json
     topics axon.event.raw
     consumer_group fluentd-behavior-events
     add_prefix behavior.
   </source>

   <filter behavior.**>
     @type record_transformer
     renew_record true
     <record>
       @timestamp ${time}
       occurredAt ${record["occurredAt"] || time}
       # properties/컨텍스트 정규화 필요 시 추가
     </record>
   </filter>

   <match behavior.**>
     @type elasticsearch
     host es-host
     port 9200
     index_name behavior-events
     type_name _doc
     logstash_format true
     include_tag_key true
     tag_key fluentd_tag
   </match>
   ```
   - `format json`: Kafka 메시지를 JSON으로 해석
   - `renew_record true`: 기존 레코드 내용을 새 JSON으로 재구성
   - `logstash_format true`: 자동으로 `behavior-events-YYYY.MM.DD` 인덱스 생성

3. Elasticsearch 인덱스 템플릿(optional)
   ```bash
   PUT _template/behavior_events_template
   {
     "index_patterns": ["behavior-events-*"] ,
     "settings": { ... },
     "mappings": {
       "properties": {
         "eventId": {"type": "long"},
         "triggerType": {"type": "keyword"},
         "properties": {"type": "object", "enabled": true}
       }
     }
   }
   ```

4. Kibana 대시보드 생성
   - Index Pattern: `behavior-events-*`
   - Visualize: `date_histogram` + `terms`(`triggerType`, `properties.campaignActivityId`, `userId`) 등

5. (선택) Fluentd에서 알람/Slack 연동
   - `@type slack` 등의 output을 추가해 오류 시 알림 가능

### 4.2 Fluentd 설정 (옵션 B: Forward → ES)

- Entry-service에 `BehaviorEventPublisherFluentd` 구현
  ```java
  @Component
  public class FluentdBehaviorEventPublisher implements BehaviorEventPublisher {
      private final FluentLogger logger = FluentLogger.getLogger("behavior.events", "fluentd-host", 24224);

      @Override
      public void publish(UserBehaviorEvent event) {
          Map<String, Object> message = Map.of(...);
          logger.log("behavior", message);
      }
  }
  ```

- Fluentd <source>
  ```conf
  <source>
    @type forward
    port 24224
  </source>

  <match behavior>
    @type elasticsearch
    ...
  </match>
  ```

- 장점: Kafka 없이도 간단
- 단점: 재처리/유실 복구에 취약, 후속 소비자 추가 어려움

### 4.3 서버 로그 수집 (STDOUT → Fluentd → ES)

- Spring Boot 컨테이너가 STDOUT으로 로그를 출력하면, Fluentd의 tail/in_tail 플러그인으로 읽어 ES에 전송
  ```conf
  <source>
    @type tail
    path /var/log/core-service.log
    pos_file /var/log/fluentd/core-service.pos
    tag server.core
    format none  # logback pattern이라면 grok 정규식
  </source>

  <match server.core>
    @type elasticsearch
    index_name server-logs
    logstash_format true
  </match>
  ```

- Kubernetes 환경이면 `fluentd-daemonset`을 사용해 pod stdout 수집 가능.

## 5. 운영 및 개발 일정 (제안)

| 단계 | 작업 내용 | 담당 | 예상 기간 |
| --- | --- | --- | --- |
| 1 | Fluentd 테스트 환경 구성 (옵션 A) | Dev A | 1주 |
| 2 | Kafka → Fluentd → ES 파이프라인 PoC | Dev A | 1주 |
| 3 | 행동 이벤트 인덱스 템플릿/매핑 설계 | Dev B | 0.5주 |
| 4 | Kibana 기본 대시보드 구성 (퍼널, 트리거별 추이) | Dev B | 1주 |
| 5 | 서버 로그 Fluentd -> ES 연동 (선택) | Dev A | 0.5주 |
| 6 | 모니터링/알람 설정 (Slack/Email) | Dev A&B | 0.5주 |
| 7 | 문서/운영 Runbook 정리, CI/CD 반영 | Dev B | 0.5주 |

※ 소규모 프로젝트 기준으로 4~5주 내 완료 가능

## 6. 향후 확장 방향

- Kafka consumer 추가 → MySQL/DWH/S3로 ETL 파이프라인 연결
- Spark/Flink로 실시간 지표 계산 → Redis/ES로 push
- Fluentd 파이프라인에 S3 output까지 추가 → 장기 보관
- 사용자 행동 로그 → ML 추천/세그먼트 분석 파이프라인 품질 개선

## 7. 참고 사항

- Fluentd/ES가 도입되면, 행동 이벤트와 서버 로그가 모두 중앙집중화되어 Kibana에서 검색과 시각화 가능
- 행동 이벤트 지표를 ES 기반으로 바로 분석 가능하지만, 장기적으로는 DWH로 옮겨 BI/세그먼트 분석에 활용하는 것도 권장
- 소규모 프로젝트일 경우, 첫 단계로 Fluentd → ES만 구축하고, 필요 시 Kafka consumer와 DWH 파이프라인을 확장하는 구조가 현실적
