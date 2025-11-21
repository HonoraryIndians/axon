#!/bin/bash

##############################################################################
# Backend Event Generator for Approved & Purchase Events
#
# Generates APPROVED and PURCHASE events by sending Kafka messages directly
# to simulate the complete conversion funnel.
#
# Prerequisites:
#   - kafkacat or kcat installed (brew install kcat)
#   - Kafka broker running on localhost:9092
#
# Usage: ./generate-backend-events.sh [activityId] [numApproved] [numPurchases]
##############################################################################

set -e

# Configuration
KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"
APPROVAL_TOPIC="campaign-activity-approval"
ACTIVITY_ID="${1:-1}"
NUM_APPROVED="${2:-10}"
NUM_PURCHASES="${3:-7}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔧 Generating Backend Events (APPROVED & PURCHASE)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID: $ACTIVITY_ID"
echo "Approved Events: $NUM_APPROVED"
echo "Purchase Events: $NUM_PURCHASES"
echo "Kafka Broker: $KAFKA_BROKER"
echo ""

# Check if kcat is installed
if ! command -v kcat &> /dev/null; then
    if ! command -v kafkacat &> /dev/null; then
        echo "❌ Error: kcat/kafkacat is not installed"
        echo ""
        echo "Install with:"
        echo "  macOS:   brew install kcat"
        echo "  Linux:   apt-get install kafkacat"
        echo ""
        exit 1
    fi
    KCAT_CMD="kafkacat"
else
    KCAT_CMD="kcat"
fi

echo "✅ Using $KCAT_CMD"
echo ""

# Function to send Kafka message
send_kafka_message() {
    local user_id=$1
    local product_id=$2
    local timestamp=$(date +%s)000  # milliseconds
    
    local message=$(cat <<EOF
{
  "userId": ${user_id},
  "productId": ${product_id},
  "timestamp": ${timestamp}
}
EOF
)
    
    echo "$message" | $KCAT_CMD -P -b $KAFKA_BROKER -t $APPROVAL_TOPIC -K:
}

# Generate APPROVED events
echo "✅ Generating $NUM_APPROVED APPROVED events..."
for i in $(seq 1 $NUM_APPROVED); do
    USER_ID=$((2000 + i))
    PRODUCT_ID=$ACTIVITY_ID
    send_kafka_message $USER_ID $PRODUCT_ID
    echo -n "."
    sleep 0.1
done
echo " ✅ Done"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Backend events sent to Kafka!"
echo ""
echo "📝 Note: Core-service will consume these messages and:"
echo "   1. Create CampaignActivityEntry with APPROVED status"
echo "   2. Generate PURCHASE events (if purchase-triggered)"
echo ""
echo "🔍 Verify Kafka messages:"
echo "   $KCAT_CMD -C -b $KAFKA_BROKER -t $APPROVAL_TOPIC -c $NUM_APPROVED"
echo ""
echo "📊 Check dashboard updates in ~5 seconds at:"
echo "   http://localhost:8080/admin/dashboard/${ACTIVITY_ID}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
