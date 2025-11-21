#!/bin/bash

##############################################################################
# 🧹 Test Data Cleanup Script
#
# 테스트로 생성된 데이터를 모두 삭제합니다:
#   - Elasticsearch: behavior-events 인덱스의 테스트 데이터
#   - MySQL: campaign_activity_entries 테이블의 테스트 데이터
#
# Usage: ./cleanup-test-data.sh [activityId]
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"
ES_URL="${ES_URL:-http://localhost:9200}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧹 Test Data Cleanup"
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
    echo "  📁 MySQL entries:        $DB_COUNT"
else
    echo "  ⚠️  Cannot connect to MySQL"
    DB_COUNT=0
fi

echo ""

if [ "$ES_COUNT" -eq 0 ] && [ "$DB_COUNT" -eq 0 ]; then
    echo "✅ No test data found for Activity $ACTIVITY_ID"
    exit 0
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 2: Confirm deletion
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "⚠️  This will DELETE:"
echo "    - $ES_COUNT Elasticsearch events"
echo "    - $DB_COUNT MySQL entries"
echo ""
read -p "Are you sure? (yes/no): " -r
echo ""

if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "❌ Cleanup cancelled"
    exit 0
fi

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 3: Delete Elasticsearch data
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
# Step 4: Delete MySQL data
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

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Cleanup completed!"
echo ""
echo "🔍 Verify deletion:"
echo "   curl '${ES_URL}/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}' | jq '.count'"
echo "   echo \"SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id=$ACTIVITY_ID;\" | $MYSQL_CMD"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
