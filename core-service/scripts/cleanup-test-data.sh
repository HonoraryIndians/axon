#!/bin/bash

##############################################################################
# 🧹 Test Data Cleanup Script
#
# 테스트로 생성된 데이터를 삭제합니다 (Activity는 유지):
#   - Elasticsearch: behavior-events 삭제
#   - MySQL: campaign_activity_entries 삭제
#   - MySQL: purchases 삭제
#   - Redis: FCFS keys 삭제
#   - Kafka: Consumer group offset 리셋
#
# ⚠️  Activity는 삭제하지 않습니다 (재사용)
#
# Usage: ./cleanup-test-data.sh [activityId]
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"
ES_URL="${ES_URL:-http://localhost:9200}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon_db}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-broker_1}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧹 Test Data Cleanup (Activity는 유지)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:   $ACTIVITY_ID"
echo "Elasticsearch: $ES_URL"
echo "Database:      $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 1: Check current data count
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "📊 Current data count:"
echo ""

# Elasticsearch count
ES_COUNT=$(curl -s "${ES_URL}/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}" | jq -r '.count' 2>/dev/null || echo "0")
echo "  📁 Elasticsearch events: $ES_COUNT"

# MySQL count
if echo "SELECT 1;" | $MYSQL_CMD > /dev/null 2>&1; then
    DB_COUNT=$($MYSQL_CMD -s -N -e "SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null || echo "0")
    PURCHASE_COUNT=$($MYSQL_CMD -s -N -e "SELECT COUNT(*) FROM purchases WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null || echo "0")
    echo "  📁 MySQL entries:        $DB_COUNT"
    echo "  📁 MySQL purchases:      $PURCHASE_COUNT"
else
    echo "  ⚠️  Cannot connect to MySQL"
    DB_COUNT=0
    PURCHASE_COUNT=0
fi

echo ""

if [ "$ES_COUNT" -eq 0 ] && [ "$DB_COUNT" -eq 0 ] && [ "$PURCHASE_COUNT" -eq 0 ]; then
    echo "✅ No test data found for Activity $ACTIVITY_ID"
    echo "💡 Activity 자체는 유지됩니다"
    exit 0
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 2: Delete Elasticsearch data
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if [ "$ES_COUNT" -gt 0 ]; then
    echo "🗑️  Deleting Elasticsearch events..."

    curl -s -X POST "${ES_URL}/behavior-events/_delete_by_query" \
        -H "Content-Type: application/json" \
        -d "{
            \"query\": {
                \"term\": {
                    \"properties.activityId\": $ACTIVITY_ID
                }
            }
        }" > /dev/null

    if [ $? -eq 0 ]; then
        echo "   ✅ Deleted $ES_COUNT events from Elasticsearch"
    else
        echo "   ❌ Error deleting from Elasticsearch"
    fi
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 3: Delete MySQL data
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if [ "$DB_COUNT" -gt 0 ]; then
    echo "🗑️  Deleting MySQL entries..."

    $MYSQL_CMD -e "DELETE FROM campaign_activity_entries WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null

    if [ $? -eq 0 ]; then
        echo "   ✅ Deleted $DB_COUNT entries from MySQL"
    else
        echo "   ❌ Error deleting from MySQL"
    fi
fi

if [ "$PURCHASE_COUNT" -gt 0 ]; then
    echo "🗑️  Deleting MySQL purchases..."

    $MYSQL_CMD -e "DELETE FROM purchases WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null

    if [ $? -eq 0 ]; then
        echo "   ✅ Deleted $PURCHASE_COUNT purchases from MySQL"
    else
        echo "   ❌ Error deleting purchases from MySQL"
    fi
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 4: Delete Redis data
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "🗑️  Deleting Redis keys..."
docker exec axon-redis redis-cli DEL "campaign:${ACTIVITY_ID}:users" "campaign:${ACTIVITY_ID}:counter" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "   ✅ Deleted Redis keys for Activity $ACTIVITY_ID"
else
    echo "   ⚠️  Failed to delete Redis keys (container might be down)"
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 5: Reset Kafka Consumer Group Offset
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "🗑️  Resetting Kafka consumer group offset..."

# Check if core-service is running
CORE_RUNNING=false
if lsof -i:8080 > /dev/null 2>&1; then
    CORE_RUNNING=true
    echo "   ⚠️  Core-service is running on port 8080"
    echo "   💡 Attempting to reset offset anyway (consumer must be inactive)"
fi

# Reset offset to latest (skip old messages)
docker exec $KAFKA_CONTAINER kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group axon-group \
    --topic axon.campaign-activity.command \
    --reset-offsets --to-latest \
    --execute > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "   ✅ Reset Kafka consumer group offset to latest"
    if [ "$CORE_RUNNING" = true ]; then
        echo "   ⚠️  Please restart core-service to apply offset changes"
    fi
else
    echo "   ⚠️  Failed to reset Kafka offset"
    echo "   💡 Make sure core-service is stopped, then run:"
    echo "      docker exec $KAFKA_CONTAINER kafka-consumer-groups --bootstrap-server localhost:9092 --group axon-group --reset-offsets --to-latest --topic axon.campaign-activity.command --execute"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Cleanup completed!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 Activity $ACTIVITY_ID는 유지되었습니다 (재사용 가능)"
echo ""
echo "🔍 Verify deletion:"
echo "   curl '${ES_URL}/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}' | jq '.count'"
echo "   echo \"SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id=$ACTIVITY_ID;\" | $MYSQL_CMD"
echo "   echo \"SELECT COUNT(*) FROM purchases WHERE campaign_activity_id=$ACTIVITY_ID;\" | $MYSQL_CMD"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
