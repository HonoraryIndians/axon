#!/bin/bash

##############################################################################
# 🚀 Setup Heavy Campaign Data (Load Test Preparation)
#
# 하나의 캠페인에 N개의 Activity를 생성하고 트래픽을 발생시킵니다.
# N+1 쿼리 성능 문제를 재현하기 위한 데이터 셋업 스크립트입니다.
#
# Usage: ./setup-heavy-campaign.sh [activityCount]
# Example: ./setup-heavy-campaign.sh 50
##############################################################################

set -e

ACTIVITY_COUNT="${1:-50}"
CAMPAIGN_ID=999

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"
DB_NAME="${DB_NAME:-axon_db}"
ES_URL="${ES_URL:-http://localhost:9200}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🏋️ Setup Heavy Campaign Data (N=$ACTIVITY_COUNT)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. 초기화
echo "🧹 Cleaning up previous load test data..."
$MYSQL_CMD -e "DELETE FROM campaign_activities WHERE campaign_id = $CAMPAIGN_ID;" 2>/dev/null
$MYSQL_CMD -e "DELETE FROM campaigns WHERE id = $CAMPAIGN_ID;" 2>/dev/null
# (ES 데이터는 남겨둠, 어차피 새로 쌓임)

# 2. 캠페인 생성
echo "🏗️  Creating Campaign #$CAMPAIGN_ID..."
$MYSQL_CMD -e "INSERT INTO campaigns (id, name, start_at, end_at, created_at, updated_at) VALUES ($CAMPAIGN_ID, 'Load Test Campaign', NOW(), NOW() + INTERVAL 30 DAY, NOW(), NOW());"

# 3. Activity N개 생성 루프
echo "🏗️  Creating $ACTIVITY_COUNT Activities..."

# Bulk Insert를 위한 루프 (성능을 위해)
# 하지만 쉘 스크립트 복잡도를 낮추기 위해 단순 루프로 처리 (데이터량이 많지 않음)
for (( i=1; i<=$ACTIVITY_COUNT; i++ )); do
    ACT_ID=$((10000 + i)) # ID 충돌 방지를 위해 10000번대 사용
    NAME="Load Test Activity $i"
    
    $MYSQL_CMD -e "INSERT INTO campaign_activities (id, campaign_id, product_id, name, activity_type, status, start_date, end_date, price, quantity, limit_count, budget, created_at, updated_at) VALUES ($ACT_ID, $CAMPAIGN_ID, 1, '$NAME', 'FIRST_COME_FIRST_SERVE', 'ACTIVE', NOW(), NOW() + INTERVAL 30 DAY, 10000, 100, 100, 1000000, NOW(), NOW());"
done

echo "   ✅ Created $ACTIVITY_COUNT activities."

# 4. 트래픽 생성 (각 Activity당 약간의 데이터만)
echo "📊 Generating minimal traffic for each activity..."
# 병렬 처리를 위해 백그라운드로 실행
PIDS=""
for (( i=1; i<=$ACTIVITY_COUNT; i++ )); do
    ACT_ID=$((10000 + i))
    # 10명 방문, 10% 클릭 (빠르게)
    "$SCRIPT_DIR/generate-full-funnel.sh" $ACT_ID 10 > /dev/null 2>&1 &
    PIDS="$PIDS $!"
    
    # 10개씩 끊어서 실행 (너무 많이 뜨면 꼬임)
    if (( i % 10 == 0 )); then
        wait $PIDS
        PIDS=""
        echo "   ... generated traffic for $i activities"
    fi
done
wait $PIDS

echo ""
echo "✅ Setup Complete! Campaign ID: $CAMPAIGN_ID"
echo "Now run: ./test-performance-latency.sh $CAMPAIGN_ID"
