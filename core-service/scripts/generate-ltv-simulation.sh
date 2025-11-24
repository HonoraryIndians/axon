#!/bin/bash

##############################################################################
# 🔮 LTV Simulation Script for Cohort Analysis
#
# 특정 Activity를 통해 유입된 고객들의 재구매 패턴을 시뮬레이션합니다.
# 코호트 분석 대시보드 테스트를 위해 30일/90일/365일 후 구매 데이터를 생성합니다.
#
# Usage: ./generate-ltv-simulation.sh [activityId]
#
# Example:
#   ./generate-ltv-simulation.sh 1
#
# Prerequisites:
#   - MySQL client installed
#   - Core-service database accessible
#   - Activity에 이미 첫 구매 고객이 존재해야 함 (k6 테스트 등으로 생성)
##############################################################################

set -e

# Configuration
ACTIVITY_ID="${1:-1}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon_db}"
DB_USER="${DB_USER:-axon_user}"
DB_PASS="${DB_PASS:-axon_password}"

# Repurchase probabilities (realistic behavior)
REPURCHASE_30D_RATE=0.70    # 70% of customers repurchase within 30 days
REPURCHASE_90D_RATE=0.40    # 40% repurchase again within 90 days
REPURCHASE_365D_RATE=0.20   # 20% become loyal customers (365 days)

# Product IDs for repurchases (different from initial FCFS product)
PRODUCT_IDS=(2 3 4 5 6 7 8 9 10)

# Price ranges for repurchases (varied amounts)
PRICE_LOW=50000
PRICE_MID=300000
PRICE_HIGH=1000000

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔮 LTV Simulation for Cohort Analysis"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:        $ACTIVITY_ID"
echo "Database:           $DB_HOST:$DB_PORT/$DB_NAME"
echo "Repurchase Rates:"
echo "  - 30 days:        ${REPURCHASE_30D_RATE}0%"
echo "  - 90 days:        ${REPURCHASE_90D_RATE}0%"
echo "  - 365 days:       ${REPURCHASE_365D_RATE}0%"
echo ""

# Step 1: Get cohort users (first-time purchasers from this activity)
echo "📊 Step 1: Identifying cohort customers..."

COHORT_QUERY="
SELECT DISTINCT p.user_id, DATE_FORMAT(MIN(p.purchase_at), '%Y-%m-%d %H:%i:%s') as first_purchase
FROM purchases p
WHERE p.campaign_activity_id = $ACTIVITY_ID
GROUP BY p.user_id
ORDER BY p.user_id;
"

# Execute query and get results
COHORT_DATA=$($MYSQL_CMD -s -N -e "$COHORT_QUERY")

if [ -z "$COHORT_DATA" ]; then
    echo "❌ Error: No cohort data found for activity $ACTIVITY_ID"
    echo ""
    echo "💡 Hint: Run the full funnel script first to generate initial purchases:"
    echo "   ./generate-full-funnel.sh $ACTIVITY_ID 100"
    exit 1
fi

# Count cohort size
COHORT_SIZE=$(echo "$COHORT_DATA" | wc -l | xargs)
echo "✅ Found $COHORT_SIZE customers in cohort"
echo ""

# Step 2: Generate repurchase data
echo "🛒 Step 2: Generating repurchase data..."

PURCHASE_COUNT_30D=0
PURCHASE_COUNT_90D=0
PURCHASE_COUNT_365D=0
TOTAL_PURCHASES=0

# Process each customer
while IFS=$'\t' read -r USER_ID FIRST_PURCHASE; do
    # Random decision for 30-day repurchase
    if [ $(( RANDOM % 100 )) -lt $(echo "$REPURCHASE_30D_RATE * 100" | bc | awk '{print int($1)}') ]; then
        # Generate 30-day repurchase
        DAYS_OFFSET=$(( 10 + RANDOM % 20 ))  # 10-30 days after first purchase
        REPURCHASE_DATE=$(date -j -v+${DAYS_OFFSET}d -f "%Y-%m-%d %H:%M:%S" "$FIRST_PURCHASE" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d "$FIRST_PURCHASE + $DAYS_OFFSET days" "+%Y-%m-%d %H:%M:%S" 2>/dev/null)

        # Random product and price
        PRODUCT_ID=${PRODUCT_IDS[$RANDOM % ${#PRODUCT_IDS[@]}]}
        PRICE=$(( PRICE_LOW + RANDOM % (PRICE_MID - PRICE_LOW) ))

        # Insert purchase
        INSERT_QUERY="
        INSERT INTO purchases (user_id, product_id, campaign_activity_id, purchase_type, price, quantity, purchase_at)
        VALUES ($USER_ID, $PRODUCT_ID, NULL, 'SHOP', $PRICE, 1, '$REPURCHASE_DATE');
        "

        $MYSQL_CMD -e "$INSERT_QUERY"
        ((PURCHASE_COUNT_30D++))
        ((TOTAL_PURCHASES++))
    fi

    # Random decision for 90-day repurchase (independent 40% probability)
    if [ $(( RANDOM % 100 )) -lt $(echo "$REPURCHASE_90D_RATE * 100" | bc | awk '{print int($1)}') ]; then
        # Generate 90-day repurchase
        DAYS_OFFSET=$(( 60 + RANDOM % 30 ))  # 60-90 days after first purchase
        REPURCHASE_DATE=$(date -j -v+${DAYS_OFFSET}d -f "%Y-%m-%d %H:%M:%S" "$FIRST_PURCHASE" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d "$FIRST_PURCHASE + $DAYS_OFFSET days" "+%Y-%m-%d %H:%M:%S" 2>/dev/null)

        # Random product and higher price (loyal customers spend more)
        PRODUCT_ID=${PRODUCT_IDS[$RANDOM % ${#PRODUCT_IDS[@]}]}
        PRICE=$(( PRICE_MID + RANDOM % (PRICE_HIGH - PRICE_MID) ))

        # Insert purchase
        INSERT_QUERY="
        INSERT INTO purchases (user_id, product_id, campaign_activity_id, purchase_type, price, quantity, purchase_at)
        VALUES ($USER_ID, $PRODUCT_ID, NULL, 'SHOP', $PRICE, 1, '$REPURCHASE_DATE');
        "

        $MYSQL_CMD -e "$INSERT_QUERY"
        ((PURCHASE_COUNT_90D++))
        ((TOTAL_PURCHASES++))
    fi

    # Random decision for 365-day repurchase (super loyal)
    if [ $(( RANDOM % 100 )) -lt $(echo "$REPURCHASE_365D_RATE * 100" | bc | awk '{print int($1)}') ]; then
        # Generate 365-day repurchase
        DAYS_OFFSET=$(( 300 + RANDOM % 65 ))  # 300-365 days after first purchase
        REPURCHASE_DATE=$(date -j -v+${DAYS_OFFSET}d -f "%Y-%m-%d %H:%M:%S" "$FIRST_PURCHASE" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d "$FIRST_PURCHASE + $DAYS_OFFSET days" "+%Y-%m-%d %H:%M:%S" 2>/dev/null)

        # Random product and premium price
        PRODUCT_ID=${PRODUCT_IDS[$RANDOM % ${#PRODUCT_IDS[@]}]}
        PRICE=$(( PRICE_HIGH + RANDOM % 500000 ))

        # Insert purchase
        INSERT_QUERY="
        INSERT INTO purchases (user_id, product_id, campaign_activity_id, purchase_type, price, quantity, purchase_at)
        VALUES ($USER_ID, $PRODUCT_ID, NULL, 'SHOP', $PRICE, 1, '$REPURCHASE_DATE');
        "

        $MYSQL_CMD -e "$INSERT_QUERY" 2>/dev/null
        ((PURCHASE_COUNT_365D++))
        ((TOTAL_PURCHASES++))
    fi

done <<< "$COHORT_DATA"

# Step 3: Summary
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ LTV Simulation Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 Results:"
echo "  Cohort Size:           $COHORT_SIZE customers"
echo "  30-day Repurchases:    $PURCHASE_COUNT_30D ($(echo "scale=1; $PURCHASE_COUNT_30D * 100 / $COHORT_SIZE" | bc)%)"
echo "  90-day Repurchases:    $PURCHASE_COUNT_90D ($(echo "scale=1; $PURCHASE_COUNT_90D * 100 / $COHORT_SIZE" | bc)%)"
echo "  365-day Repurchases:   $PURCHASE_COUNT_365D ($(echo "scale=1; $PURCHASE_COUNT_365D * 100 / $COHORT_SIZE" | bc)%)"
echo "  Total New Purchases:   $TOTAL_PURCHASES"
echo ""
echo "🎯 Next Steps:"
echo "  1. View cohort dashboard:"
echo "     http://localhost:8080/admin/dashboard/cohort/$ACTIVITY_ID"
echo ""
echo "  2. Verify data:"
echo "     ./verify-dashboard-data.sh $ACTIVITY_ID"
echo ""
echo "  3. Check LTV metrics via API:"
echo "     curl http://localhost:8080/api/v1/dashboard/cohort/activity/$ACTIVITY_ID | jq"
echo ""
echo "💡 Expected Metrics:"
echo "  - Repeat Purchase Rate: ~70% ($(echo "scale=1; ($PURCHASE_COUNT_30D + $PURCHASE_COUNT_90D + $PURCHASE_COUNT_365D) * 100 / $COHORT_SIZE / 2" | bc)%)"
echo "  - Avg Purchase Frequency: ~2-3 times"
echo "  - LTV/CAC Ratio: Should be > 3.0 for healthy ROI"
echo ""
