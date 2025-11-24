# 📊 Dashboard Test Scripts

대시보드 테스트를 위한 자동화 스크립트 모음입니다.

## 🚀 Quick Start (권장)

### ⭐ `run-dashboard-test.sh` - 완전 자동화 테스트

**한 줄로 모든 것을 자동화!**

```bash
./run-dashboard-test.sh [activityId] [numVisitors]

# 예시: Activity 1번에 100명 방문자
./run-dashboard-test.sh 1 100
```

**자동으로 수행:**
1. ✅ Activity 존재 확인 (없으면 자동 생성)
2. ✅ 이전 테스트 데이터 정리 (Kafka offset 리셋 포함)
3. ✅ 퍼널 데이터 생성 (PAGE_VIEW → CLICK → APPROVED → PURCHASE)
4. ✅ 데이터 검증
5. ✅ 대시보드 URL 출력

**결과:**
- 📊 실시간 대시보드: http://localhost:8080/admin/dashboard/1
- 📈 코호트 대시보드: http://localhost:8080/admin/dashboard/cohort/1

**반복 실행:**
```bash
# 계속 반복 가능 (Activity 재사용, 데이터만 새로 생성)
./run-dashboard-test.sh 1 100
./run-dashboard-test.sh 1 200  # 다른 방문자 수로 테스트
./run-dashboard-test.sh 1 50
```

---

## 📖 개별 스크립트 사용법

### 1. `generate-full-funnel.sh` - Conversion Funnel 생성

전체 퍼널 데이터를 생성합니다.

```bash
./generate-full-funnel.sh [activityId] [numVisitors]

# 예시
./generate-full-funnel.sh 1 100
```

**생성되는 데이터:**
- 👁️ **PAGE_VIEW**: 100 events → Elasticsearch
- 👆 **CLICK**: 40 events (40% conversion) → Elasticsearch
- ✅ **APPROVED**: 12 entries (30% of clicks) → MySQL + Elasticsearch
- 💰 **PURCHASE**: 자동 생성 (= APPROVED 수) → MySQL + Elasticsearch

**참고:**
- PURCHASE는 백엔드에서 자동 생성됩니다 (PurchaseHandler)
- Activity가 미리 존재해야 합니다

### 2. `cleanup-test-data.sh` - 테스트 데이터 정리

테스트 데이터를 삭제합니다 (Activity는 유지).

```bash
./cleanup-test-data.sh [activityId]

# 예시
./cleanup-test-data.sh 1
```

**삭제 항목:**
- ✅ Elasticsearch: behavior-events 삭제
- ✅ MySQL: campaign_activity_entries 삭제
- ✅ MySQL: purchases 삭제
- ✅ Redis: FCFS keys 삭제
- ✅ **Kafka: Consumer group offset 리셋**

**유지 항목:**
- ✅ Activity (campaign_activities) - 재사용 가능!

### 3. `verify-ltv-workflow.sh` - 데이터 검증

전체 워크플로우의 데이터 무결성을 검증합니다.

```bash
./verify-ltv-workflow.sh [activityId]

# 예시
./verify-ltv-workflow.sh 1
```

**검증 항목:**
- ✅ Activity 존재 확인
- ✅ Entries vs Purchases 일치 여부
- ✅ Repurchase 데이터 통계
- ✅ Elasticsearch 이벤트 집계
- ✅ Cohort API 응답 검증

---

## 🔮 LTV 시뮬레이션 테스트

### 시나리오: 고객 재구매 패턴 분석

```bash
# 1. 기본 퍼널 데이터 생성
./run-dashboard-test.sh 1 100

# 2. 데이터를 30일 전으로 이동 (Time Travel)
./time-travel-activity.sh 1 30

# 3. 재구매 데이터 생성
./generate-ltv-simulation.sh 1

# 4. 코호트 대시보드 확인
open http://localhost:8080/admin/dashboard/cohort/1
```

**예상 결과:**
- 👥 Cohort Size: 12 customers
- 💰 LTV 30d: ₩1,800,000
- 📈 LTV 90d: ₩2,500,000
- 🔄 Repeat Purchase Rate: ~70%

---

## 🔍 검증 및 디버깅

### Elasticsearch 확인

```bash
# 전체 이벤트 수
curl http://localhost:9200/behavior-events/_count

# Activity별 이벤트 타입
curl -s 'http://localhost:9200/behavior-events/_search' \
  -H 'Content-Type: application/json' \
  -d '{
    "size": 0,
    "query": {"term": {"properties.activityId": 1}},
    "aggs": {"by_type": {"terms": {"field": "triggerType.keyword"}}}
  }' | jq '.aggregations.by_type.buckets'
```

### MySQL 확인

```bash
# Entries 상태별 카운트
mysql -u axon_user -paxon_password axon_db -e "
  SELECT status, COUNT(*) FROM campaign_activity_entries
  WHERE campaign_activity_id = 1 GROUP BY status;
"

# Purchases 확인
mysql -u axon_user -paxon_password axon_db -e "
  SELECT COUNT(*) as total, COUNT(DISTINCT user_id) as unique_users
  FROM purchases WHERE campaign_activity_id = 1;
"
```

### Kafka Consumer Group 확인

```bash
# Offset 상태 확인
docker exec broker_1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group axon-group \
  --describe

# Offset 수동 리셋 (문제 발생 시)
docker exec broker_1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group axon-group \
  --topic axon.campaign-activity.command \
  --reset-offsets --to-latest \
  --execute
```

---

## 💡 트러블슈팅

### Q1: "존재하지 않는 캠페인 활동입니다" 에러

**원인:** Kafka topic에 이전 메시지가 남아있지만 Activity가 삭제됨

**해결:**
```bash
# Option 1: 올인원 스크립트 사용 (자동 해결)
./run-dashboard-test.sh 1 100

# Option 2: Kafka offset 수동 리셋
docker exec broker_1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group axon-group \
  --reset-offsets --to-latest \
  --topic axon.campaign-activity.command \
  --execute
```

### Q2: Purchase 데이터가 생성 안 됨

**원인:** BackendEventPublisher 미구현 또는 Kafka 메시지 처리 실패

**확인:**
```bash
# Core-service 로그 확인
tail -f core-service/logs/application.log | grep -i purchase

# Kafka consumer group lag 확인
docker exec broker_1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group axon-group \
  --describe
```

### Q3: MySQL과 Elasticsearch 데이터 불일치

**원인:** Kafka 파이프라인 지연 또는 에러

**해결:**
```bash
# 1. Cleanup 후 재생성
./cleanup-test-data.sh 1
./generate-full-funnel.sh 1 100

# 2. Kafka Connect 상태 확인
curl http://localhost:8083/connectors
curl http://localhost:8083/connectors/elasticsearch-sink-behavior-events/status
```

---

## 📋 스크립트 목록

| 스크립트 | 용도 | 사용 빈도 |
|---------|------|----------|
| `run-dashboard-test.sh` | ⭐ 완전 자동화 테스트 | 매일 |
| `generate-full-funnel.sh` | 퍼널 데이터 생성 | 수동 실행 시 |
| `cleanup-test-data.sh` | 테스트 데이터 정리 | 수동 실행 시 |
| `verify-ltv-workflow.sh` | 데이터 검증 | 디버깅 시 |
| `generate-ltv-simulation.sh` | LTV 재구매 시뮬레이션 | LTV 테스트 시 |
| `time-travel-activity.sh` | 데이터 과거 이동 | LTV 테스트 시 |

---

## ⚙️ 환경 설정

### 필수 환경변수

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_USER=axon_user
export DB_PASS=axon_password
export DB_NAME=axon_db
export ES_URL=http://localhost:9200
export ENTRY_SERVICE_URL=http://localhost:8081
```

### 필수 서비스

테스트 전에 다음 서비스가 실행 중이어야 합니다:

- ✅ **Core-service** (port 8080)
- ✅ **Entry-service** (port 8081)
- ✅ **MySQL** (port 3306)
- ✅ **Redis** (port 6379)
- ✅ **Kafka** (port 9092)
- ✅ **Elasticsearch** (port 9200)
- ✅ **Kafka Connect** (port 8083)

```bash
# 모든 서비스 시작
docker-compose up -d

# 서비스 상태 확인
docker-compose ps
```

---

## 🎯 Best Practices

1. **항상 올인원 스크립트 사용**
   ```bash
   ./run-dashboard-test.sh 1 100
   ```

2. **Activity는 한 번만 생성하고 재사용**
   - Cleanup 시 Activity 삭제 안 됨
   - 설정(가격, 수량, 예산) 유지

3. **문제 발생 시 Kafka offset 리셋**
   - Cleanup 스크립트가 자동으로 리셋
   - 수동 리셋도 가능

4. **LTV 테스트는 별도 프로세스**
   - Time travel → Simulation → Dashboard 확인

5. **검증 스크립트로 데이터 확인**
   ```bash
   ./verify-ltv-workflow.sh 1
   ```

---

## 📚 참고 문서

- **Architecture**: `docs/purchase-event-flow.md`
- **Marketing Dashboard**: `docs/marketing-dashboard-spec.md`
- **LTV Analysis**: `docs/plan/marketing-dashboard-development-plan.md`
- **Performance**: `docs/performance-improvement-plan.md`
