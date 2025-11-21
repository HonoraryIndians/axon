#!/bin/bash

##############################################################################
# 🎯 Complete Conversion Funnel Generator
#
# 하나의 스크립트로 완전한 conversion funnel 데이터를 생성합니다:
#   1. PAGE_VIEW (Elasticsearch)
#   2. CLICK (Elasticsearch)
#   3. APPROVED (MySQL)
#   4. PURCHASE (MySQL - optional via APPROVED trigger)
#
# Usage: ./generate-full-funnel.sh [activityId] [numVisitors]
##############################################################################

set -e

# Configuration
ENTRY_SERVICE_URL="${ENTRY_SERVICE_URL:-http://localhost:8081}"
ACTIVITY_ID="${1:-1}"
NUM_VISITORS="${2:-100}"

# Database config
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

# Conversion rates (realistic funnel)
CLICK_RATE=0.4        # 40% of visitors click
APPROVED_RATE=0.3     # 30% of clickers get approved
PURCHASE_RATE=0.7     # 70% of approved complete purchase

# Calculate funnel counts
VISITORS=$NUM_VISITORS
CLICKERS=$(echo "$VISITORS * $CLICK_RATE" | bc | awk '{print int($1)}')
APPROVED=$(echo "$CLICKERS * $APPROVED_RATE" | bc | awk '{print int($1)}')
PURCHASES=$(echo "$APPROVED * $PURCHASE_RATE" | bc | awk '{print int($1)}')

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 Complete Conversion Funnel Generator"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID:    $ACTIVITY_ID"
echo "Entry Service:  $ENTRY_SERVICE_URL"
echo "Database:       $DB_NAME@$DB_HOST:$DB_PORT"
echo ""
echo "Expected Funnel:"
echo "  👁️  Visitors:  $VISITORS (100%)"
echo "  👆 Clicks:    $CLICKERS (${CLICK_RATE}% → $(echo "$CLICKERS * 100 / $VISITORS" | bc)%)"
echo "  ✅ Approved:  $APPROVED (${APPROVED_RATE}% → $(echo "$APPROVED * 100 / $VISITORS" | bc)%)"
echo "  💰 Purchases: $PURCHASES (${PURCHASE_RATE}% → $(echo "$PURCHASES * 100 / $VISITORS" | bc)%)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Function to send behavior event
send_event() {
    local trigger_type=$1
    local user_id=$2
    local session_id=$3

    curl -s -X POST "${ENTRY_SERVICE_URL}/api/v1/behavior/events" \
        -H "Content-Type: application/json" \
        -d @- > /dev/null <<EOF
{
  "eventName": "${trigger_type}_test",
  "triggerType": "${trigger_type}",
  "occurredAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "userId": ${user_id},
  "sessionId": "${session_id}",
  "pageUrl": "http://localhost:8080/campaign-activity/${ACTIVITY_ID}/detail",
  "referrer": "http://localhost:8080/campaigns",
  "userAgent": "FunnelBot/1.0",
  "properties": {
    "activityId": ${ACTIVITY_ID},
    "source": "funnel-test-script"
  }
}
EOF
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 1: Generate PAGE_VIEW events (Elasticsearch)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "📊 Step 1/3: Generating $VISITORS PAGE_VIEW events..."
for i in $(seq 1 $VISITORS); do
    USER_ID=$((5000 + i))
    SESSION_ID="funnel-session-${USER_ID}"
    send_event "PAGE_VIEW" $USER_ID $SESSION_ID
    echo -n "."
    if [ $((i % 50)) -eq 0 ]; then
        echo " [$i/$VISITORS]"
    fi
done
echo " ✅ Done ($VISITORS visits)"
sleep 1

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 2: Generate CLICK events (Elasticsearch)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo ""
echo "📊 Step 2/3: Generating $CLICKERS CLICK events..."
for i in $(seq 1 $CLICKERS); do
    USER_ID=$((5000 + i))
    SESSION_ID="funnel-session-${USER_ID}"
    send_event "CLICK" $USER_ID $SESSION_ID
    echo -n "."
    if [ $((i % 50)) -eq 0 ]; then
        echo " [$i/$CLICKERS]"
    fi
    sleep 0.05  # Small delay to avoid overwhelming server
done
echo " ✅ Done ($CLICKERS clicks)"
sleep 1

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 3: Generate APPROVED via entry-service test API
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo ""
echo "📊 Step 3/4: Generating $APPROVED reservations (APPROVED events)..."

SUCCESS_COUNT=0
for i in $(seq 1 $APPROVED); do
    USER_ID=$((5000 + i))
    
    # Call entry-service TEST endpoint (no auth required, !prod only)
    RESPONSE=$(curl -s -X POST "${ENTRY_SERVICE_URL}/api/v1/test/reserve/${USER_ID}" \
        -H "Content-Type: application/json" \
        -d @- <<EOF
{
  "campaignActivityId": ${ACTIVITY_ID},
  "productId": ${ACTIVITY_ID}
}
EOF
)
    
    # Check if reservation succeeded
    if echo "$RESPONSE" | grep -q '"success":true'; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -n "✓"
    else
        echo -n "✗"
    fi
    
    if [ $((i % 50)) -eq 0 ]; then
        echo " [$i/$APPROVED]"
    fi
    sleep 0.1  # Avoid overwhelming entry-service
done

echo ""
echo "✅ Done ($SUCCESS_COUNT/$APPROVED reservations succeeded)"
echo "   → ReservationApprovedEvent → BackendEventPublisher → Kafka → Elasticsearch"


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Step 4: Generate PURCHASE events (Elasticsearch)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo ""
echo "📊 Step 4/4: Generating $PURCHASES PURCHASE events..."
for i in $(seq 1 $PURCHASES); do
    USER_ID=$((5000 + i))
    SESSION_ID="funnel-session-${USER_ID}"
    send_event "PURCHASE" $USER_ID $SESSION_ID
    echo -n "."
    if [ $((i % 50)) -eq 0 ]; then
        echo " [$i/$PURCHASES]"
    fi
    sleep 0.05
done
echo " ✅ Done ($PURCHASES purchases)"

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Summary & Verification
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Conversion Funnel Generated Successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 Generated Data:"
echo "  👁️  PAGE_VIEW:  $VISITORS events → Elasticsearch"
echo "  👆 CLICK:      $CLICKERS events → Elasticsearch"
echo "  ✅ APPROVED:   $SUCCESS_COUNT reservations → Elasticsearch (via entry-service)"
echo "  💰 PURCHASE:   $PURCHASES events → Elasticsearch"
echo ""
echo "🔍 Verification Commands:"
echo ""
echo "  # Elasticsearch events by type (should show all 4 types)"
echo "  curl -s 'http://localhost:9200/behavior-events/_search' -H 'Content-Type: application/json' -d '{\"size\":0,\"query\":{\"term\":{\"properties.activityId\":${ACTIVITY_ID}}},\"aggs\":{\"by_type\":{\"terms\":{\"field\":\"triggerType.keyword\"}}}}' | jq '.aggregations.by_type.buckets'"
echo ""
echo "  # Dashboard API (should show complete funnel)"
echo "  curl 'http://localhost:8080/api/v1/dashboard/activity/${ACTIVITY_ID}?period=7d' | jq '.funnel'"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 View Dashboard (updates every 5 seconds):"
echo "   http://localhost:8080/admin/dashboard/${ACTIVITY_ID}"
echo ""
echo "🧹 Clean up test data:"
echo "   ./cleanup-test-data.sh ${ACTIVITY_ID}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
