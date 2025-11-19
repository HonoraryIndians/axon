#!/bin/bash

##############################################################################
# Dashboard Data Verification Script
#
# Checks if there's enough data in the system to test the dashboard
##############################################################################

set -e

ES_URL="${ES_URL:-http://localhost:9200}"
ACTIVITY_ID="${1:-1}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 Verifying Dashboard Data for Activity $ACTIVITY_ID"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check Elasticsearch connection
echo "1️⃣  Checking Elasticsearch connection..."
if curl -s "${ES_URL}/_cluster/health" > /dev/null 2>&1; then
    echo "   ✅ Elasticsearch is running"
else
    echo "   ❌ Elasticsearch is not accessible at ${ES_URL}"
    exit 1
fi

# Check behavior-events index exists
echo ""
echo "2️⃣  Checking behavior-events index..."
if curl -s "${ES_URL}/behavior-events" > /dev/null 2>&1; then
    echo "   ✅ behavior-events index exists"

    # Count total events
    TOTAL_EVENTS=$(curl -s "${ES_URL}/behavior-events/_count" | grep -o '"count":[0-9]*' | grep -o '[0-9]*')
    echo "   📊 Total events: $TOTAL_EVENTS"
else
    echo "   ⚠️  behavior-events index not found"
    echo "   💡 Events will be created when Kafka Connect starts"
fi

# Count events by type for this activity
echo ""
echo "3️⃣  Checking events for Activity $ACTIVITY_ID..."

# PAGE_VIEW count
PAGE_VIEWS=$(curl -s -X POST "${ES_URL}/behavior-events/_count" \
    -H "Content-Type: application/json" \
    -d "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"triggerType\":\"PAGE_VIEW\"}},{\"wildcard\":{\"pageUrl\":\"*activity/${ACTIVITY_ID}*\"}}]}}}" \
    | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# CLICK count
CLICKS=$(curl -s -X POST "${ES_URL}/behavior-events/_count" \
    -H "Content-Type: application/json" \
    -d "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"triggerType\":\"CLICK\"}},{\"wildcard\":{\"pageUrl\":\"*activity/${ACTIVITY_ID}*\"}}]}}}" \
    | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# PURCHASE count
PURCHASES=$(curl -s -X POST "${ES_URL}/behavior-events/_count" \
    -H "Content-Type: application/json" \
    -d "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"triggerType\":\"PURCHASE\"}},{\"wildcard\":{\"pageUrl\":\"*activity/${ACTIVITY_ID}*\"}}]}}}" \
    | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

echo "   📊 Funnel Events:"
echo "      👁️  PAGE_VIEW: $PAGE_VIEWS"
echo "      👆 CLICK:     $CLICKS"
echo "      💰 PURCHASE:  $PURCHASES"

# Check MySQL for APPROVED count
echo ""
echo "4️⃣  Checking MySQL for APPROVED entries..."
echo "   💡 Run this SQL to check:"
echo "   SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id = ${ACTIVITY_ID} AND status = 'APPROVED';"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Dashboard Data Summary"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$PAGE_VIEWS" -gt 0 ] && [ "$CLICKS" -gt 0 ]; then
    echo "✅ Good! You have frontend events (PAGE_VIEW, CLICK)"
    echo "   Dashboard funnel will show meaningful data"
else
    echo "⚠️  Missing frontend events!"
    echo "   💡 Run: ./scripts/generate-test-events.sh ${ACTIVITY_ID} 50"
fi

if [ "$PURCHASES" -gt 0 ]; then
    echo "✅ Great! You have backend PURCHASE events"
    echo "   Full funnel visualization will work"
else
    echo "⚠️  No PURCHASE events yet"
    echo "   💡 These are created when users complete payment"
fi

echo ""
echo "🚀 Ready to test dashboard?"
echo "   1. REST API:  curl http://localhost:8080/api/v1/dashboard/activity/${ACTIVITY_ID}"
echo "   2. SSE:       curl -N http://localhost:8080/api/v1/dashboard/stream/activity/${ACTIVITY_ID}"
echo "   3. Browser:   http://localhost:8080/dashboard/activity/${ACTIVITY_ID}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
