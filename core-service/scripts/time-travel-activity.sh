#!/bin/bash

##############################################################################
# ⏰ Time Travel Script for LTV Simulation
#
# 특정 Activity의 데이터를 과거로 이동시킵니다.
# 이를 통해 "현재 시점"에서 생성된 데이터를 "과거 데이터"로 변환하여
# LTV 시뮬레이션(재구매)을 위한 코호트를 형성합니다.
#
# Usage: ./time-travel-activity.sh [activityId] [days]
#
# Example:
#   ./time-travel-activity.sh 5 30  (5번 액티비티 데이터를 30일 전으로 이동)
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"
DAYS="${2:-30}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon_db}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⏰ Time Travel Simulation"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:    $ACTIVITY_ID"
echo "Time Shift:     -$DAYS days"
echo "Database:       $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Check if data exists
ENTRY_COUNT=$($MYSQL_CMD -s -N -e "SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null || echo "0")
PURCHASE_COUNT=$($MYSQL_CMD -s -N -e "SELECT COUNT(*) FROM purchases WHERE campaign_activity_id = $ACTIVITY_ID;" 2>/dev/null || echo "0")

if [ "$ENTRY_COUNT" -eq 0 ] && [ "$PURCHASE_COUNT" -eq 0 ]; then
    echo "❌ No data found for Activity $ACTIVITY_ID"
    exit 1
fi

echo "📊 Found data to move:"
echo "  - Entries:   $ENTRY_COUNT"
echo "  - Purchases: $PURCHASE_COUNT"
echo ""
echo "⚠️  Moving data to $DAYS days ago..."

# Update campaign_activity_entries
echo "  ⏳ Updating entries..."
$MYSQL_CMD -e "UPDATE campaign_activity_entries SET created_at = created_at - INTERVAL $DAYS DAY, updated_at = updated_at - INTERVAL $DAYS DAY WHERE campaign_activity_id = $ACTIVITY_ID;"

# Update purchases (Cohort)
echo "  ⏳ Updating purchases..."
$MYSQL_CMD -e "UPDATE purchases SET purchase_at = purchase_at - INTERVAL $DAYS DAY WHERE campaign_activity_id = $ACTIVITY_ID;"

# Update campaign activity period
echo "  ⏳ Updating campaign activity period..."
$MYSQL_CMD -e "UPDATE campaign_activities SET start_date = start_date - INTERVAL $DAYS DAY, end_date = end_date - INTERVAL $DAYS DAY WHERE id = $ACTIVITY_ID;"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Time Travel Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Now you can run the LTV simulation script to generate repurchases:"
echo "   ./core-service/scripts/generate-ltv-simulation.sh $ACTIVITY_ID"
echo ""
