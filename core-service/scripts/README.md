# 📊 Dashboard Test Event Generation Scripts

대시보드 Conversion Funnel 테스트를 위한 자동화 스크립트 모음입니다.

## 🌟 **추천: 통합 스크립트로 한 번에 실행!**

### `generate-full-funnel.sh` - 완전한 Conversion Funnel 자동 생성 🚀

**가장 간단하고 강력한 방법!** 한 줄로 전체 퍼널 데이터를 생성합니다.

```bash
./generate-full-funnel.sh [activityId] [numVisitors]

# 예시: Activity 1번에 100명의 방문자로 완전한 퍼널 생성
./generate-full-funnel.sh 1 100
```

**자동 생성되는 데이터:**
- 👁️ **PAGE_VIEW**: 100 events (100%)
- 👆 **CLICK**: 40 events (40% conversion)
- ✅ **APPROVED**: 12 entries (30% of clicks)
- 💰 **PURCHASE**: 자동 트리거 (70% of approved)

**필수 환경변수** (MySQL 사용 시):
```bash
export DB_USER=root
export DB_PASS=your_password
./generate-full-funnel.sh 1 100
```

---

## 🔧 개별 스크립트 (고급 사용자용)

필요시 퍼널의 특정 단계만 별도로 실행할 수 있습니다.

### 1️⃣ `generate-test-events.sh` - 프론트엔드 이벤트
PAGE_VIEW와 CLICK만 생성합니다.

```bash
./generate-test-events.sh [activityId] [numUsers]
```

---

### 2️⃣ `generate-db-events.sh` - APPROVED 이벤트 (추천! 🌟)
**가장 간단하고 안정적인 방법**. MySQL 데이터베이스에 직접 INSERT합니다.

```bash
./generate-db-events.sh [activityId] [numApproved]

# 예시: Activity 1번에 20개의 APPROVED 엔트리 생성
./generate-db-events.sh 1 20
```

**필수 조건:**
- MySQL 클라이언트 설치 (`mysql` 명령어)
- 데이터베이스 접속 권한

**환경변수 설정:**
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_USER=root
export DB_PASS=your_password
export DB_NAME=axon
```

**생성되는 데이터:**
- ✅ APPROVED 상태의 `campaign_activity_entries` 레코드

---

### 3️⃣ `generate-backend-events.sh` - Kafka 기반 (고급)
Kafka 메시지를 직접 발행하여 백엔드 이벤트를 생성합니다.

```bash
./generate-backend-events.sh [activityId] [numApproved] [numPurchases]

# 예시
./generate-backend-events.sh 1 15 10
```

**필수 조건:**
- `kcat` 또는 `kafkacat` 설치
  ```bash
  brew install kcat  # macOS
  apt-get install kafkacat  # Linux
  ```

**생성되는 이벤트:**
- Kafka 토픽: `campaign-activity-approval`
- Core-service가 consume하여 APPROVED/PURCHASE 처리

---

### 4️⃣ `generate-approved-purchases.sh` - REST API 기반
REST API를 호출하여 백엔드 이벤트를 생성합니다.

```bash
./generate-approved-purchases.sh [activityId] [numEvents]

# 예시
./generate-approved-purchases.sh 1 15
```

**참고:** 이 스크립트는 테스트 엔드포인트가 구현되어 있어야 작동합니다.

---

## 🚀 완전한 퍼널 테스트 시나리오

전체 conversion funnel을 테스트하려면 다음 순서로 실행하세요:

```bash
# 1. 프론트엔드 이벤트 생성 (Visit, Click)
./generate-test-events.sh 1 100

# 2. APPROVED 엔트리 생성 (30개)
./generate-db-events.sh 1 30

# 3. 대시보드 확인
open http://localhost:8080/admin/dashboard/1
```

**예상 결과:**
- 👁️ **Total Visits**: 100
- 👆 **Total Clicks**: 40 (40% conversion)
- ✅ **Approved**: 30
- 💰 **Purchases**: 0 (별도 생성 안 함)

---

## 🔍 검증 방법

### Elasticsearch 확인
```bash
curl http://localhost:9200/behavior-events/_count
curl "http://localhost:9200/behavior-events/_search?q=properties.activityId:1&size=10&pretty"
```

### MySQL 확인
```bash
mysql -u root -p axon -e "
  SELECT status, COUNT(*) as count
  FROM campaign_activity_entries
  WHERE campaign_activity_id = 1
  GROUP BY status;
"
```

### Dashboard API 확인
```bash
curl "http://localhost:8080/api/v1/dashboard/activity/1?period=7d" | jq '.'
```

---

## 💡 팁

1. **실시간 업데이트 확인**: 대시보드는 5초마다 자동 갱신됩니다.
2. **브라우저 캐시**: 차트가 안 바뀌면 `Cmd+Shift+R` (하드 리프레시)
3. **데이터 초기화**: 테스트 데이터를 삭제하려면:
   ```bash
   # Elasticsearch
   curl -X DELETE http://localhost:9200/behavior-events/_doc/_query?q=properties.activityId:1
   
   # MySQL
   mysql -u root -p axon -e "DELETE FROM campaign_activity_entries WHERE campaign_activity_id = 1;"
   ```

---

## 📊 Dashboard 주소

- **Activity Dashboard**: http://localhost:8080/admin/dashboard/1
- **API Endpoint**: http://localhost:8080/api/v1/dashboard/activity/1

---

## ⚠️ 주의사항

- PURCHASE 이벤트는 `CampaignActivityEntry`가 APPROVED 상태이고, Activity 타입이 purchase-related일 때 자동 생성됩니다.
- 스크립트 실행 전 서비스들이 정상 동작하는지 확인하세요:
  - ✅ Core-service (port 8080)
  - ✅ Entry-service (port 8081)
  - ✅ Kafka (port 9092)
  - ✅ Elasticsearch (port 9200)
  - ✅ MySQL (port 3306)
