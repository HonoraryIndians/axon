#!/bin/bash

##############################################################################
# Simple Backend Event Generator via REST API
#
# Generates APPROVED and PURCHASE events by calling backend APIs directly.
# This is simpler than Kafka approach and works out of the box.
#
# Usage: ./generate-approved-purchases.sh [activityId] [numEvents]
##############################################################################

set -e

# Configuration
CORE_SERVICE_URL="${CORE_SERVICE_URL:-http://localhost:8080}"
ACTIVITY_ID="${1:-1}"
NUM_EVENTS="${2:-10}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 Generating APPROVED & PURCHASE Events via API"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID: $ACTIVITY_ID"
echo "Events: $NUM_EVENTS"
echo "Core Service: $CORE_SERVICE_URL"
echo ""

# Get JWT token first (for authenticated endpoints)
# NOTE: Adjust this based on your authentication setup
echo "🔑 Getting authentication token..."
JWT_TOKEN=$(curl -s -X POST "${CORE_SERVICE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' | jq -r '.token' 2>/dev/null || echo "")

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" = "null" ]; then
    echo "⚠️  Warning: Could not get JWT token. Trying without authentication..."
    AUTH_HEADER=""
else
    echo "✅ Got JWT token"
    AUTH_HEADER="Authorization: Bearer $JWT_TOKEN"
fi

echo ""

# Function to create campaign activity entry (APPROVED status)
create_approved_entry() {
    local user_id=$1
    local product_id=$ACTIVITY_ID
    
    # This simulates what Kafka consumer does
    # We'll insert directly into database via test endpoint
    # Or call internal service method if exposed
    
    curl -s -X POST "${CORE_SERVICE_URL}/api/test/campaign-activity-entry" \
        -H "Content-Type: application/json" \
        ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
        -d @- > /dev/null <<EOF
{
  "activityId": ${ACTIVITY_ID},
  "userId": ${user_id},
  "productId": ${product_id},
  "status": "APPROVED"
}
EOF
}

# Function to create purchase event
create_purchase() {
    local user_id=$1
    
    curl -s -X POST "${CORE_SERVICE_URL}/api/test/purchase" \
        -H "Content-Type: application/json" \
        ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
        -d @- > /dev/null <<EOF
{
  "activityId": ${ACTIVITY_ID},
  "userId": ${user_id},
  "amount": 10000
}
EOF
}

echo "✅ Generating $NUM_EVENTS APPROVED events..."
for i in $(seq 1 $NUM_EVENTS); do
    USER_ID=$((3000 + i))
    create_approved_entry $USER_ID
    echo -n "."
    sleep 0.05
done
echo " ✅ Done"

# Generate purchases (70% of approved)
NUM_PURCHASES=$(echo "$NUM_EVENTS * 0.7" | bc | awk '{print int($1)}')
echo "💰 Generating $NUM_PURCHASES PURCHASE events..."
for i in $(seq 1 $NUM_PURCHASES); do
    USER_ID=$((3000 + i))
    create_purchase $USER_ID
    echo -n "."
    sleep 0.05
done
echo " ✅ Done"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Backend events generated!"
echo ""
echo "📊 Dashboard should update in ~5 seconds:"
echo "   http://localhost:8080/admin/dashboard/${ACTIVITY_ID}"
echo ""
echo "🔍 Verify in database:"
echo "   SELECT status, COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id=$ACTIVITY_ID GROUP BY status;"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
