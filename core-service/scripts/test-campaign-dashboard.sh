#!/bin/bash

##############################################################################
# 🚀 Campaign Level Dashboard Test
#
# 캠페인 하나에 여러 Activity가 있을 때 데이터가 잘 합산되는지 테스트합니다.
#
# Usage: ./test-campaign-dashboard.sh
##############################################################################

set -e

# Configuration
CAMPAIGN_ID=1
ACTIVITY_A=1
ACTIVITY_B=2

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"
DB_NAME="${DB_NAME:-axon_db}"
ES_URL="${ES_URL:-http://localhost:9200}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Campaign Dashboard Test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. 초기화 & 데이터 셋업
echo "🧹 Initializing & Setting up data..."

# Redis 정리 (중복 참여 방지)
echo "   🗑️  Cleaning up Redis..."
docker exec axon-redis redis-cli DEL "campaign:1:users" "campaign:2:users" "campaign:1:counter" "campaign:2:counter" > /dev/null 2>&1

# ES 정리
curl -s -X POST "${ES_URL}/behavior-events/_delete_by_query" \
    -H "Content-Type: application/json" \
    -d '{"query": {"match_all": {}}}' > /dev/null

# MySQL 데이터 셋업
cat "$SCRIPT_DIR/setup-campaign-data.sql" | $MYSQL_CMD 2>&1 > /dev/null
echo "   ✅ Data setup complete (Campaign 1, Activity 1 & 2)"

# 2. 퍼널 데이터 생성 (Activity 1: 아이폰)
echo ""
echo "📊 Generating traffic for Activity 1 (iPhone)..."
# 100명 방문, 10명 구매
"$SCRIPT_DIR/generate-full-funnel.sh" $ACTIVITY_A 100 > /dev/null
echo "   ✅ Activity 1: 100 Visits generated"

# 3. 퍼널 데이터 생성 (Activity 2: 맥북)
echo ""
echo "📊 Generating traffic for Activity 2 (MacBook)..."
# 50명 방문, (구매는 generate-full-funnel 내부 확률에 따름 -> 약 5~6명 예상)
"$SCRIPT_DIR/generate-full-funnel.sh" $ACTIVITY_B 50 > /dev/null
echo "   ✅ Activity 2: 50 Visits generated"

echo ""
echo "⏳ Waiting for ES indexing (5s)..."
sleep 5

# 4. API 검증
echo ""
echo "🔍 Verifying Campaign Dashboard API..."
RESPONSE=$(curl -s "http://localhost:8080/api/v1/dashboard/campaign/$CAMPAIGN_ID")

# jq로 파싱해서 검증
TOTAL_VISITS=$(echo "$RESPONSE" | jq '.overview.totalVisits')
TOTAL_PURCHASES=$(echo "$RESPONSE" | jq '.overview.purchaseCount')
ACTIVITY_COUNT=$(echo "$RESPONSE" | jq '.activities | length')

echo "   🔹 Campaign Overview:"
echo "      Total Visits:    $TOTAL_VISITS (Expected: ~150)"
echo "      Total Purchases: $TOTAL_PURCHASES"
echo "      Activity Count:  $ACTIVITY_COUNT (Expected: 2)"

if [ "$ACTIVITY_COUNT" -eq 2 ]; then
    echo "   ✅ Test PASSED: Campaign dashboard aggregated 2 activities."
else
    echo "   ❌ Test FAILED: Activity count mismatch."
    exit 1
fi

echo ""
echo "🔗 Campaign Dashboard JSON:"
echo "   http://localhost:8080/api/v1/dashboard/campaign/$CAMPAIGN_ID"
echo ""
