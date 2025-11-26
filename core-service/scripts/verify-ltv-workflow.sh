#!/bin/bash

##############################################################################
# 🔍 LTV Workflow Verification Script
#
# 전체 LTV 시뮬레이션 워크플로우의 데이터 무결성을 검증합니다.
#
# Usage: ./verify-ltv-workflow.sh [activityId]
#
# Example:
#   ./verify-ltv-workflow.sh 1
#
# Checks:
#   1. Campaign Activity 기본 정보
#   2. Entries vs Purchases 일치 여부
#   3. Elasticsearch 이벤트 카운트
#   4. 코호트 분석 API 응답
#   5. LTV 데이터 존재 여부
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon_db}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"

ES_HOST="${ES_HOST:-localhost:9200}"
API_HOST="${API_HOST:-localhost:8080}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 LTV Workflow Verification"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:    $ACTIVITY_ID"
echo "Database:       $DB_NAME@$DB_HOST:$DB_PORT"
echo "Elasticsearch:  $ES_HOST"
echo "API:            $API_HOST"
echo ""

# Step 1: Campaign Activity 정보
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 1: Campaign Activity 기본 정보"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ACTIVITY_INFO=$($MYSQL_CMD -s -N -e "
SELECT
    id,
    name,
    activity_type,
    status,
    price,
    quantity,
    budget
FROM campaign_activities
WHERE id = $ACTIVITY_ID;
" 2>/dev/null)

if [ -z "$ACTIVITY_INFO" ]; then
    echo -e "${RED}❌ Activity $ACTIVITY_ID not found${NC}"
    exit 1
fi

echo "$ACTIVITY_INFO" | awk -F'\t' '{
    printf "  ID:           %s\n", $1
    printf "  Name:         %s\n", $2
    printf "  Type:         %s\n", $3
    printf "  Status:       %s\n", $4
    printf "  Price:        ₩%s\n", $5
    printf "  Quantity:     %s\n", $6
    printf "  Budget:       ₩%s\n", $7
}'
echo ""

# Step 2: Entries 상태 확인
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 2: Campaign Activity Entries"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ENTRY_STATS=$($MYSQL_CMD -s -N -e "
SELECT
    status,
    COUNT(*) as count
FROM campaign_activity_entries
WHERE campaign_activity_id = $ACTIVITY_ID
GROUP BY status;
" 2>/dev/null)

if [ -z "$ENTRY_STATS" ]; then
    echo -e "${YELLOW}⚠️  No entries found${NC}"
    APPROVED_COUNT=0
else
    echo "$ENTRY_STATS" | awk -F'\t' '{printf "  %s: %s\n", $1, $2}'
    APPROVED_COUNT=$(echo "$ENTRY_STATS" | grep "APPROVED" | awk -F'\t' '{print $2}' || echo "0")
fi
echo ""

# Step 3: Purchases 확인
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 3: Purchases"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PURCHASE_STATS=$($MYSQL_CMD -s -N -e "
SELECT
    purchase_type,
    COUNT(*) as count,
    COUNT(DISTINCT user_id) as unique_users
FROM purchases
WHERE campaign_activity_id = $ACTIVITY_ID
GROUP BY purchase_type;
" 2>/dev/null)

INITIAL_PURCHASE_COUNT=0
if [ -z "$PURCHASE_STATS" ]; then
    echo -e "${YELLOW}⚠️  No purchases found for campaign_activity_id=$ACTIVITY_ID${NC}"
else
    echo "$PURCHASE_STATS" | awk -F'\t' '{printf "  %s: %s purchases (%s unique users)\n", $1, $2, $3}'
    INITIAL_PURCHASE_COUNT=$(echo "$PURCHASE_STATS" | grep "CAMPAIGNACTIVITY" | awk -F'\t' '{print $3}' || echo "0")
fi
echo ""

# Step 4: Entries vs Purchases 일치 확인
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 Step 4: Data Consistency Check"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Get user IDs from entries
ENTRY_USERS=$($MYSQL_CMD -s -N -e "
SELECT DISTINCT user_id
FROM campaign_activity_entries
WHERE campaign_activity_id = $ACTIVITY_ID AND status = 'APPROVED'
ORDER BY user_id;
" 2>/dev/null)

# Get user IDs from purchases
PURCHASE_USERS=$($MYSQL_CMD -s -N -e "
SELECT DISTINCT user_id
FROM purchases
WHERE campaign_activity_id = $ACTIVITY_ID
ORDER BY user_id;
" 2>/dev/null)

if [ "$ENTRY_USERS" = "$PURCHASE_USERS" ]; then
    echo -e "${GREEN}✅ Entries and Purchases are consistent${NC}"
    echo "   APPROVED Entries: $APPROVED_COUNT"
    echo "   Initial Purchases: $INITIAL_PURCHASE_COUNT"
else
    echo -e "${RED}❌ Mismatch detected!${NC}"
    echo ""
    echo "Users in APPROVED entries but NOT in purchases:"
    comm -23 <(echo "$ENTRY_USERS") <(echo "$PURCHASE_USERS") | head -5
    echo ""
    echo "Users in purchases but NOT in APPROVED entries:"
    comm -13 <(echo "$ENTRY_USERS") <(echo "$PURCHASE_USERS") | head -5
fi
echo ""

# Step 5: Repurchase 데이터 확인 (LTV 시뮬레이션)
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 5: Repurchase Data (LTV Simulation)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Get cohort user IDs
COHORT_USERS=$(echo "$ENTRY_USERS" | wc -l | xargs)

# Get repurchases (SHOP type, no campaign_activity_id)
REPURCHASE_STATS=$($MYSQL_CMD -s -N -e "
SELECT
    COUNT(*) as total_repurchases,
    COUNT(DISTINCT user_id) as users_who_repurchased
FROM purchases
WHERE user_id IN (
    SELECT DISTINCT user_id
    FROM campaign_activity_entries
    WHERE campaign_activity_id = $ACTIVITY_ID AND status = 'APPROVED'
)
AND campaign_activity_id IS NULL
AND purchase_type = 'SHOP';
" 2>/dev/null)

if [ -z "$REPURCHASE_STATS" ]; then
    echo -e "${YELLOW}⚠️  No repurchase data found${NC}"
    echo "   💡 Run: ./generate-ltv-simulation.sh $ACTIVITY_ID"
else
    TOTAL_REPURCHASES=$(echo "$REPURCHASE_STATS" | awk '{print $1}')
    USERS_REPURCHASED=$(echo "$REPURCHASE_STATS" | awk '{print $2}')
    REPEAT_RATE=$(echo "scale=1; $USERS_REPURCHASED * 100 / $COHORT_USERS" | bc)

    echo "  Total Repurchases:    $TOTAL_REPURCHASES"
    echo "  Users Repurchased:    $USERS_REPURCHASED / $COHORT_USERS"
    echo "  Repeat Purchase Rate: ${REPEAT_RATE}%"
fi
echo ""

# Step 6: Elasticsearch 이벤트 확인
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 6: Elasticsearch Behavior Events"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ES_RESPONSE=$(curl -s "http://$ES_HOST/behavior-events/_search" \
    -H 'Content-Type: application/json' \
    -d "{
        \"size\": 0,
        \"query\": {
            \"term\": {
                \"properties.activityId\": $ACTIVITY_ID
            }
        },
        \"aggs\": {
            \"by_type\": {
                \"terms\": {
                    \"field\": \"triggerType.keyword\"
                }
            }
        }
    }" 2>/dev/null)

if echo "$ES_RESPONSE" | grep -q "error"; then
    echo -e "${YELLOW}⚠️  Elasticsearch not available or no data${NC}"
else
    echo "$ES_RESPONSE" | jq -r '.aggregations.by_type.buckets[] | "  \(.key): \(.doc_count)"' 2>/dev/null || echo "  (jq not installed, skipping)"
fi
echo ""

# Step 7: Cohort API 확인
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Step 7: Cohort Analysis API"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

API_RESPONSE=$(curl -s "http://$API_HOST/api/v1/dashboard/cohort/activity/$ACTIVITY_ID" 2>/dev/null)

if echo "$API_RESPONSE" | grep -q "error"; then
    echo -e "${RED}❌ API error${NC}"
    echo "$API_RESPONSE" | jq '.' 2>/dev/null || echo "$API_RESPONSE"
else
    if command -v jq &> /dev/null; then
        COHORT_SIZE=$(echo "$API_RESPONSE" | jq -r '.cohortSize // "N/A"')
        LTV_30D=$(echo "$API_RESPONSE" | jq -r '.ltv30d // "0"')
        LTV_90D=$(echo "$API_RESPONSE" | jq -r '.ltv90d // "0"')
        LTV_365D=$(echo "$API_RESPONSE" | jq -r '.ltv365d // "0"')
        AVG_CAC=$(echo "$API_RESPONSE" | jq -r '.avgCac // "0"')

        echo "  Cohort Size:     $COHORT_SIZE"
        echo "  LTV 30d:         ₩$LTV_30D"
        echo "  LTV 90d:         ₩$LTV_90D"
        echo "  LTV 365d:        ₩$LTV_365D"
        echo "  Avg CAC:         ₩$AVG_CAC"

        if [ "$LTV_30D" != "0" ]; then
            echo -e "  ${GREEN}✅ LTV data exists${NC}"
        else
            echo -e "  ${YELLOW}⚠️  No LTV data (repurchases not found)${NC}"
        fi
    else
        echo "  (jq not installed, raw response:)"
        echo "$API_RESPONSE"
    fi
fi
echo ""

# Final Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Verification Complete"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Summary:"
echo "  - APPROVED Entries:       $APPROVED_COUNT"
echo "  - Initial Purchases:      $INITIAL_PURCHASE_COUNT"
echo "  - Cohort Size (API):      ${COHORT_SIZE:-N/A}"
echo ""
echo "🎯 Next Steps:"
echo "  1. View dashboard:        http://$API_HOST/admin/dashboard/$ACTIVITY_ID"
echo "  2. View cohort dashboard: http://$API_HOST/admin/dashboard/cohort/$ACTIVITY_ID"
echo ""
