#!/bin/bash

##############################################################################
# Activity 3-11에 대량 퍼널 데이터 생성
# 각 Activity당 약 25,000건의 이벤트 생성
##############################################################################

set -e

# Configuration
START_ACTIVITY_ID=3
END_ACTIVITY_ID=11
NUM_VISITORS_PER_ACTIVITY=15000  # 15,000명 방문 → 약 24,600 이벤트

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FUNNEL_SCRIPT="$SCRIPT_DIR/generate-full-funnel.sh"

# Check if funnel script exists
if [ ! -f "$FUNNEL_SCRIPT" ]; then
    echo "❌ Error: $FUNNEL_SCRIPT not found!"
    exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 Bulk Funnel Data Generator"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity Range: $START_ACTIVITY_ID - $END_ACTIVITY_ID"
echo "Visitors/Activity: $NUM_VISITORS_PER_ACTIVITY"
echo "Expected Events/Activity: ~$((NUM_VISITORS_PER_ACTIVITY * 164 / 100))"
echo ""
echo "⚠️  Total Expected Events: ~$(((END_ACTIVITY_ID - START_ACTIVITY_ID + 1) * NUM_VISITORS_PER_ACTIVITY * 164 / 100))"
echo "⏱️  Estimated Time: ~$((END_ACTIVITY_ID - START_ACTIVITY_ID + 1)) minutes"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Confirm
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Process each activity
TOTAL_START=$(date +%s)

for ACTIVITY_ID in $(seq $START_ACTIVITY_ID $END_ACTIVITY_ID); do
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎯 Processing Activity ID: $ACTIVITY_ID"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    ACTIVITY_START=$(date +%s)

    # Run funnel generation
    if "$FUNNEL_SCRIPT" "$ACTIVITY_ID" "$NUM_VISITORS_PER_ACTIVITY"; then
        ACTIVITY_END=$(date +%s)
        ACTIVITY_DURATION=$((ACTIVITY_END - ACTIVITY_START))
        echo "✅ Activity $ACTIVITY_ID completed in ${ACTIVITY_DURATION}s"
    else
        echo "❌ Activity $ACTIVITY_ID failed!"
        read -p "Continue with next activity? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Stopping."
            exit 1
        fi
    fi

    # Small delay between activities
    echo "⏸️  Cooling down for 5 seconds..."
    sleep 5
done

TOTAL_END=$(date +%s)
TOTAL_DURATION=$((TOTAL_END - TOTAL_START))
TOTAL_MINUTES=$((TOTAL_DURATION / 60))
TOTAL_SECONDS=$((TOTAL_DURATION % 60))

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ All Activities Completed!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Total Time: ${TOTAL_MINUTES}m ${TOTAL_SECONDS}s"
echo ""
echo "📊 Verification Commands:"
echo ""
for ACTIVITY_ID in $(seq $START_ACTIVITY_ID $END_ACTIVITY_ID); do
    echo "# Activity $ACTIVITY_ID:"
    echo "curl 'http://localhost:9200/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}' | jq '.count'"
done
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
