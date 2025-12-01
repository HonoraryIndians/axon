#!/bin/bash

##############################################################################
# 🚀 One-Click Dashboard Test Script
#
# 완전 자동화된 대시보드 테스트:
#   1. 완전 초기화 (Activity 포함 모든 데이터 삭제)
#   2. Activity 생성 (setup SQL 실행)
#   3. 퍼널 데이터 생성
#   4. 데이터 검증
#   5. 대시보드 URL 출력
#
# Usage: ./run-dashboard-test.sh [activityId] [numVisitors]
#
# Example:
#   ./run-dashboard-test.sh 1 100
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"
NUM_VISITORS="${2:-100}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon_db}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"
ES_URL="${ES_URL:-http://localhost:9200}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-broker_1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 One-Click Dashboard Test (완전 자동화)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:    $ACTIVITY_ID"
echo "Visitors:       $NUM_VISITORS"
echo "Database:       $DB_NAME@$DB_HOST:$DB_PORT"
echo ""
echo "⚠️  완전 초기화 모드: Activity 포함 모든 데이터 재생성"
echo ""


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 1: 완전 초기화 (MySQL cleanup via robust SQL script)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "🧹 Step 1/5: 완전 초기화 (모든 데이터 삭제)..."
echo ""

# 1-1. Elasticsearch 정리 (기존 로직 유지)
echo "  🗑️  Elasticsearch 정리..."
ES_COUNT=$(curl -s "${ES_URL}/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}" | jq -r '.count' 2>/dev/null || echo "0")
if [ "$ES_COUNT" -gt 0 ]; then
    curl -s -X POST "${ES_URL}/behavior-events/_delete_by_query" \
        -H "Content-Type: application/json" \
        -d "{\"query\": {\"term\": {\"properties.activityId\": $ACTIVITY_ID}}}" > /dev/null
    echo "     ✅ Deleted $ES_COUNT events"
else
    echo "     ℹ️  No events to delete"
fi

# 1-2. MySQL 정리 (robust cleanup)
echo "  🗑️  MySQL 정리 (테스트 유저 및 데이터)..."
"$SCRIPT_DIR/cleanup-test-data.sh" "$ACTIVITY_ID" "$MYSQL_CMD" # Pass activity ID and MYSQL_CMD
if [ $? -eq 0 ]; then
    echo "     ✅ Deleted all test data for Activity $ACTIVITY_ID"
else
    echo "     ❌ Error deleting MySQL data during cleanup. Exiting."
    exit 1
fi

# 1-3. Redis 정리
echo "  🗑️  Redis 정리..."
docker exec axon-redis redis-cli DEL "campaign:${ACTIVITY_ID}:users" "campaign:${ACTIVITY_ID}:counter" > /dev/null 2>&1
echo "     ✅ Deleted Redis keys"

# 1-4. Kafka offset 리셋
echo "  🗑️  Kafka offset 리셋..."
docker exec $KAFKA_CONTAINER kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group axon-group \
    --topic axon.campaign-activity.command \
    --reset-offsets --to-latest \
    --execute > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "     ✅ Reset Kafka consumer group offset"
else
    echo "     ⚠️  Failed to reset Kafka offset (core-service might be running)"
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 2: Activity 생성 (using templated SQL script)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "🏗️  Step 2/5: Activity 생성..."
echo ""

SETUP_SQL_TEMPLATE="$SCRIPT_DIR/setup-activity-data.sql"
TEMP_SETUP_SQL="/tmp/setup_activity_${ACTIVITY_ID}.sql"

if [ ! -f "$SETUP_SQL_TEMPLATE" ]; then
    echo "   ❌ Error: setup-activity-data.sql not found at $SETUP_SQL_TEMPLATE"
    exit 1
fi

# Replace placeholders in setup SQL
# Ensure variables are set
if [ -z "$CAMPAIGN_ID" ]; then CAMPAIGN_ID=$ACTIVITY_ID; fi
if [ -z "$PRODUCT_ID" ]; then PRODUCT_ID=$ACTIVITY_ID; fi
if [ -z "$TEST_USER_ID_MIN" ]; then TEST_USER_ID_MIN=1000; fi

# Use a simpler sed approach (compatible with both GNU and BSD sed)
sed "s/ACTIVITY_ID_PLACEHOLDER/$ACTIVITY_ID/g" "$SETUP_SQL_TEMPLATE" > "$TEMP_SETUP_SQL.tmp"
sed "s/CAMPAIGN_ID_PLACEHOLDER/$CAMPAIGN_ID/g" "$TEMP_SETUP_SQL.tmp" > "$TEMP_SETUP_SQL"
sed "s/PRODUCT_ID_PLACEHOLDER/$PRODUCT_ID/g" "$TEMP_SETUP_SQL" > "$TEMP_SETUP_SQL.tmp"
sed "s/TEST_USER_ID_MIN/$TEST_USER_ID_MIN/g" "$TEMP_SETUP_SQL.tmp" > "$TEMP_SETUP_SQL"
rm "$TEMP_SETUP_SQL.tmp"

# Debug: Check the generated SQL
echo "🔍 Debugging SQL Content:"
cat "$TEMP_SETUP_SQL"
echo "---------------------------------------------------"

# Execute setup SQL (showing errors if any)
cat "$TEMP_SETUP_SQL" | $MYSQL_CMD

if [ $? -eq 0 ]; then
    # Verify Activity creation
    ACTIVITY_EXISTS=$($MYSQL_CMD -s -N -e "SELECT COUNT(*) FROM campaign_activities WHERE id = $ACTIVITY_ID;" 2>/dev/null || echo "0")
    if [ "$ACTIVITY_EXISTS" -eq 1 ]; then
        echo "   ✅ Activity $ACTIVITY_ID created successfully"
    else
        echo "   ❌ Error: SQL executed but Activity $ACTIVITY_ID not found in DB"
        exit 1
    fi
else
    echo "   ❌ Error: SQL execution failed (check above for MySQL errors)"
    exit 1
fi
rm "$TEMP_SETUP_SQL"

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 3: 퍼널 데이터 생성
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "📊 Step 3/5: 퍼널 데이터 생성..."
echo ""

"$SCRIPT_DIR/generate-full-funnel.sh" "$ACTIVITY_ID" "$NUM_VISITORS"

echo ""

# Wait for data propagation
echo "⏳ Waiting for data propagation (5 seconds)..."
sleep 5
echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 4: 데이터 검증
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "🔍 Step 4/5: 데이터 검증..."
echo ""

"$SCRIPT_DIR/verify-ltv-workflow.sh" "$ACTIVITY_ID"

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 5: 완료 및 대시보드 URL
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 Dashboard Test Completed Successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 View Dashboards:"
echo ""
echo "   🔗 실시간 대시보드:"
echo "      http://localhost:8080/admin/dashboard/$ACTIVITY_ID"
echo ""
echo "   🔗 코호트 분석 대시보드:"
echo "      http://localhost:8080/admin/dashboard/cohort/$ACTIVITY_ID"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🔄 반복 실행:"
echo "   ./run-dashboard-test.sh $ACTIVITY_ID $NUM_VISITORS"
echo ""
echo "🧪 LTV 테스트:"
echo "   1. ./time-travel-activity.sh $ACTIVITY_ID 30"
echo "   2. ./generate-ltv-simulation.sh $ACTIVITY_ID"
echo "   3. http://localhost:8080/admin/dashboard/cohort/$ACTIVITY_ID"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
