#!/bin/bash

##############################################################################
# 🎬 Axon FCFS Load Test - 통합 준비 스크립트
##############################################################################
#
# 사용 방법:
#   ./prepare-load-test.sh [NUM_USERS] [ACTIVITY_ID]
#
# 예시:
#   ./prepare-load-test.sh 100 1      # 100명, Activity 1
#   ./prepare-load-test.sh 1000 1     # 1000명, Activity 1
#   ./prepare-load-test.sh 8000 1     # 8000명, Activity 1
#
# 자동 생성:
#   1. MySQL 유저 (등급 분포: BRONZE 60%, SILVER 30%, GOLD 10%)
#   2. Redis 캐싱 (90% 캐시, 10% 미스)
#   3. JWT 토큰 (배치 병렬 발급)
#
# 결과:
#   - jwt-tokens.json (발급된 토큰)
#   - 필터 통과율: 40% (SILVER+GOLD)
#   - Redis 캐시 히트율: 90%
##############################################################################

set -e

# ============================================================================
# 설정
# ============================================================================
NUM_USERS="${1:-1000}"
ACTIVITY_ID="${2:-1}"

USER_ID_START=1000
USER_ID_END=$((USER_ID_START + NUM_USERS - 1))

# 서비스 URL
CORE_SERVICE_URL="${CORE_SERVICE_URL:-http://localhost:8080}"
ENTRY_SERVICE_URL="${ENTRY_SERVICE_URL:-http://localhost:8081}"

# MySQL 설정
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"
DB_NAME="${DB_NAME:-axon_db}"

# Redis 설정
REDIS_MODE="${REDIS_MODE:-k8s}" # k8s or docker
REDIS_PASSWORD="${REDIS_PASSWORD:-axon1234}"

# 스크립트 디렉토리
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/jwt-tokens.json"

# ============================================================================
# 비율 계산
# ============================================================================
BRONZE_COUNT=$((NUM_USERS * 60 / 100))
SILVER_COUNT=$((NUM_USERS * 30 / 100))
GOLD_COUNT=$((NUM_USERS - BRONZE_COUNT - SILVER_COUNT))

# ID 범위 계산
BRONZE_START=$USER_ID_START
BRONZE_END=$((BRONZE_START + BRONZE_COUNT - 1))
SILVER_START=$((BRONZE_END + 1))
SILVER_END=$((SILVER_START + SILVER_COUNT - 1))
GOLD_START=$((SILVER_END + 1))
GOLD_END=$USER_ID_END

# Redis 캐시 비율 (90%)
CACHE_COUNT=$((NUM_USERS * 90 / 100))
CACHE_START=$USER_ID_START
CACHE_END=$((CACHE_START + CACHE_COUNT - 1))
NO_CACHE_START=$((CACHE_END + 1))
NO_CACHE_END=$USER_ID_END

# ============================================================================
# 헤더 출력
# ============================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎬 Axon FCFS Load Test 준비 시작"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "총 유저 수: $NUM_USERS"
echo "Activity ID: $ACTIVITY_ID"
echo ""
echo "📊 등급 분포 (필터 통과율 40%):"
echo "   BRONZE: $BRONZE_COUNT (60%) - ID $BRONZE_START-$BRONZE_END"
echo "   SILVER: $SILVER_COUNT (30%) - ID $SILVER_START-$SILVER_END"
echo "   GOLD:   $GOLD_COUNT (10%) - ID $GOLD_START-$GOLD_END"
echo "   → 필터 통과: $((SILVER_COUNT + GOLD_COUNT)) (40%)"
echo ""
echo "💾 Redis 캐시 분포 (히트율 90%):"
echo "   캐시 있음: $CACHE_COUNT (90%) - ID $CACHE_START-$CACHE_END"
echo "   캐시 없음: $((NUM_USERS - CACHE_COUNT)) (10%) - ID $NO_CACHE_START-$NO_CACHE_END"
echo ""
echo "🔗 서비스:"
echo "   Core:  $CORE_SERVICE_URL"
echo "   DB:    $DB_HOST:$DB_PORT ($DB_USER)"
echo "   Redis: $REDIS_MODE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ============================================================================
# Step 1: Redis 초기화
# ============================================================================
echo "🧹 Step 1/5: Redis 초기화..."

if [ "$REDIS_MODE" == "docker" ]; then
    echo "   Docker 모드로 실행..."
    docker exec axon-redis redis-cli -a "$REDIS_PASSWORD" DEL \
      "campaign:${ACTIVITY_ID}:users" \
      "campaign:${ACTIVITY_ID}:counter" \
      > /dev/null 2>&1
else
    echo "   K8s 모드로 실행..."
    # Pod 이름 동적 조회
    REDIS_POD=$(kubectl get pods -l app.kubernetes.io/name=redis -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "axon-redis-master-0")
    kubectl exec "$REDIS_POD" -- redis-cli -a "$REDIS_PASSWORD" DEL \
      "campaign:${ACTIVITY_ID}:users" \
      "campaign:${ACTIVITY_ID}:counter" \
      > /dev/null 2>&1
fi

echo "   ✅ Redis 초기화 완료"
echo ""

# ============================================================================
# Step 2: MySQL 유저 생성
# ============================================================================
echo "👥 Step 2/5: MySQL 테스트 유저 생성 중..."

# MySQL 비밀번호 환경변수 설정
export MYSQL_PWD="$DB_PASS"
MYSQL_CMD_BASE="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"

# 기존 테스트 유저 삭제
$MYSQL_CMD_BASE -e "DELETE FROM users WHERE id BETWEEN $USER_ID_START AND $USER_ID_END;" 2>/dev/null

# BRONZE 유저 생성 (60%)
if [ $BRONZE_COUNT -gt 0 ]; then
  echo "   생성 중: BRONZE $BRONZE_COUNT 명..."
  $MYSQL_CMD_BASE << EOF
INSERT INTO users (id, name, email, grade, role, created_at, updated_at)
SELECT
    n as id,
    CONCAT('test_bronze_', n) as name,
    CONCAT('bronze_', n, '@test.com') as email,
    'BRONZE' as grade,
    'USER' as role,
    NOW() as created_at,
    NOW() as updated_at
FROM (
    SELECT $BRONZE_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000) as n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d
    WHERE ($BRONZE_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000)) BETWEEN $BRONZE_START AND $BRONZE_END
) numbers;
EOF
fi

# SILVER 유저 생성 (30%)
if [ $SILVER_COUNT -gt 0 ]; then
  echo "   생성 중: SILVER $SILVER_COUNT 명..."
  $MYSQL_CMD_BASE << EOF
INSERT INTO users (id, name, email, grade, role, created_at, updated_at)
SELECT
    n as id,
    CONCAT('test_silver_', n) as name,
    CONCAT('silver_', n, '@test.com') as email,
    'SILVER' as grade,
    'USER' as role,
    NOW() as created_at,
    NOW() as updated_at
FROM (
    SELECT $SILVER_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000) as n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d
    WHERE ($SILVER_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000)) BETWEEN $SILVER_START AND $SILVER_END
) numbers;
EOF
fi

# GOLD 유저 생성 (10%)
if [ $GOLD_COUNT -gt 0 ]; then
  echo "   생성 중: GOLD $GOLD_COUNT 명..."
  $MYSQL_CMD_BASE << EOF
INSERT INTO users (id, name, email, grade, role, created_at, updated_at)
SELECT
    n as id,
    CONCAT('test_gold_', n) as name,
    CONCAT('gold_', n, '@test.com') as email,
    'GOLD' as grade,
    'USER' as role,
    NOW() as created_at,
    NOW() as updated_at
FROM (
    SELECT $GOLD_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000) as n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d
    WHERE ($GOLD_START + (a.N + b.N * 10 + c.N * 100 + d.N * 1000)) BETWEEN $GOLD_START AND $GOLD_END
) numbers;
EOF
fi

echo "   ✅ MySQL 유저 생성 완료"
echo ""

# ============================================================================
# Step 3: Redis 캐싱 (90%)
# ============================================================================
echo "💾 Step 3/5: Redis 유저 캐싱 중 (90%)..."

# 캐싱할 유저들의 정보를 MySQL에서 조회
CACHE_DATA=$(mktemp)
$MYSQL_CMD_BASE -N -e "SELECT id, name, email, grade FROM users WHERE id BETWEEN $CACHE_START AND $CACHE_END;" > "$CACHE_DATA"

# Redis PIPELINE으로 배치 캐싱
REDIS_PIPELINE=$(mktemp)
while IFS=$'\t' read -r id name email grade;
do
  cat >> "$REDIS_PIPELINE" << EOF
HSET user:${id} id ${id}
HSET user:${id} name ${name}
HSET user:${id} email ${email}
HSET user:${id} grade ${grade}
EOF
done < "$CACHE_DATA"

# Redis에 PIPELINE 실행
if [ "$REDIS_MODE" == "docker" ]; then
    cat "$REDIS_PIPELINE" | docker exec -i axon-redis redis-cli -a "$REDIS_PASSWORD" > /dev/null 2>&1
else
    # Pod 이름 다시 조회
    REDIS_POD=$(kubectl get pods -l app.kubernetes.io/name=redis -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "axon-redis-master-0")
    cat "$REDIS_PIPELINE" | kubectl exec -i "$REDIS_POD" -- redis-cli -a "$REDIS_PASSWORD" > /dev/null 2>&1
fi

rm -f "$CACHE_DATA" "$REDIS_PIPELINE"

echo "   ✅ Redis 캐싱 완료 ($CACHE_COUNT 명)"
echo ""

# ============================================================================
# Step 4: JWT 토큰 발급 (배치 병렬 처리)
# ============================================================================
echo "🔐 Step 4/5: JWT 토큰 발급 중 (배치 병렬)..."

# 토큰 파일 초기화
echo "{" > "$TOKEN_FILE"

BATCH_SIZE=100
TOTAL_BATCHES=$(( (NUM_USERS + BATCH_SIZE - 1) / BATCH_SIZE ))

for ((batch=0; batch<TOTAL_BATCHES; batch++)); do
  START_USER=$((USER_ID_START + batch * BATCH_SIZE))
  END_USER=$((START_USER + BATCH_SIZE - 1))

  if [ $END_USER -gt $USER_ID_END ]; then
    END_USER=$USER_ID_END
  fi

  # 임시 파일 (프로세스별)
  TEMP_TOKENS=$(mktemp)

  # 배치 내 병렬 처리
  for ((userId=START_USER; userId<=END_USER; userId++)); do
    (
      TOKEN=$(curl -s "${CORE_SERVICE_URL}/test/auth/token?userId=${userId}")
      if [[ ${#TOKEN} -gt 20 ]]; then
          echo "  \"${userId}\": \"${TOKEN}\"," >> "$TEMP_TOKENS"
      fi
    ) &
  done

  # 배치 완료 대기
  wait

  # 결과 병합
  cat "$TEMP_TOKENS" >> "$TOKEN_FILE"
  rm -f "$TEMP_TOKENS"

  # 진행률 출력
  PROGRESS=$(( (batch + 1) * 100 / TOTAL_BATCHES ))
  echo "   진행률: [$PROGRESS%] Batch $((batch + 1))/$TOTAL_BATCHES"
done

# JSON 마무리 (마지막 쉼표 제거)
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' '$ s/,$//' "$TOKEN_FILE"
else
  sed -i '$ s/,$//' "$TOKEN_FILE"
fi
echo "}" >> "$TOKEN_FILE"

echo "   ✅ JWT 토큰 발급 완료!"
echo ""

# ============================================================================
# Step 5: 검증
# ============================================================================
echo "✅ Step 5/5: 검증 중..."

# MySQL 유저 수 확인
USER_COUNT=$($MYSQL_CMD_BASE -s -N -e "SELECT COUNT(*) FROM users WHERE id BETWEEN $USER_ID_START AND $USER_ID_END;")
echo "   MySQL 유저: $USER_COUNT / $NUM_USERS"

# JWT 토큰 수 확인
TOKEN_COUNT=$(grep -c '"' "$TOKEN_FILE" || echo "0")
TOKEN_COUNT=$((TOKEN_COUNT / 2))  # key:value이므로 2로 나눔
echo "   JWT 토큰: $TOKEN_COUNT / $NUM_USERS"

# Redis 캐시 확인
if [ "$REDIS_MODE" == "docker" ]; then
    REDIS_COUNT=$(docker exec -i axon-redis redis-cli -a "$REDIS_PASSWORD" KEYS "user:*" | wc -l | tr -d ' ')
else
    REDIS_POD=$(kubectl get pods -l app.kubernetes.io/name=redis -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "axon-redis-master-0")
    REDIS_COUNT=$(kubectl exec -i "$REDIS_POD" -- redis-cli -a "$REDIS_PASSWORD" KEYS "user:*" | wc -l | tr -d ' ')
fi
echo "   Redis 캐시: $REDIS_COUNT / $CACHE_COUNT"

if [ "$USER_COUNT" -eq "$NUM_USERS" ] && [ "$TOKEN_COUNT" -ge "$NUM_USERS" ]; then
  echo "   ✅ 검증 성공!"
else
  echo "   ⚠️  경고: 일부 데이터 누락"
fi

echo ""

# ============================================================================
# 완료
# ============================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 준비 완료!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 다음 단계: k6 부하 테스트 실행"
echo ""
echo "   # Spike 시나리오 (Thunder Herd)"
echo "   SCENARIO=spike MAX_VUS=$NUM_USERS USE_PRODUCTION_API=true k6 run k6-fcfs-load-test.js"
echo ""
echo "   # Constant 시나리오 (계단식)"
echo "   SCENARIO=constant VUS_LIST=\"100,500,1000\" USE_PRODUCTION_API=true k6 run k6-fcfs-load-test.js"
echo ""
echo "📊 예상 결과:"
echo "   - 필터 통과: $((SILVER_COUNT + GOLD_COUNT)) 명 (40%)"
echo "   - FCFS 성공: ~100 명"
echo "   - FCFS 마감: ~$((SILVER_COUNT + GOLD_COUNT - 100)) 명"
echo "   - 필터 실패: $BRONZE_COUNT 명 (60%)"
echo ""
echo "⏰ 주의: JWT 토큰은 30분 후 만료됩니다!"
echo "   테스트는 30분 이내에 진행하세요."
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
