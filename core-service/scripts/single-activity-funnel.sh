#!/bin/bash

##############################################################################
# 단일 Activity 퍼널 데이터 생성 (포트포워딩 포함)
# Usage: ./single-activity-funnel.sh <activity-id> [num-visitors] [namespace]
##############################################################################

set -e

ACTIVITY_ID=${1:-3}
NUM_VISITORS=${2:-15000}
NAMESPACE=${3:-default}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 Single Activity Funnel Generator"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID: $ACTIVITY_ID"
echo "Visitors: $NUM_VISITORS"
echo "Namespace: $NAMESPACE"
echo "Expected Events: ~$((NUM_VISITORS * 164 / 100))"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if already port-forwarded
if curl -s http://localhost:9200 >/dev/null 2>&1; then
    echo "✅ ES already accessible on localhost:9200"
    ES_ALREADY_RUNNING=true
else
    echo "🔧 Starting ES port-forward..."
    kubectl port-forward -n "$NAMESPACE" svc/elasticsearch 9200:9200 >/dev/null 2>&1 &
    ES_PID=$!
    ES_ALREADY_RUNNING=false
    sleep 2
fi

if curl -s http://localhost:8081/actuator/health >/dev/null 2>&1; then
    echo "✅ Entry Service already accessible on localhost:8081"
    ENTRY_ALREADY_RUNNING=true
else
    echo "🔧 Starting Entry Service port-forward..."
    kubectl port-forward -n "$NAMESPACE" svc/entry-service 8081:8081 >/dev/null 2>&1 &
    ENTRY_PID=$!
    ENTRY_ALREADY_RUNNING=false
    sleep 2
fi

# Verify connections
echo ""
echo "🔍 Verifying connections..."
if curl -s http://localhost:9200 >/dev/null 2>&1; then
    ES_VERSION=$(curl -s http://localhost:9200 | jq -r '.version.number' 2>/dev/null || echo "unknown")
    echo "  ✅ ES connected (version: $ES_VERSION)"
else
    echo "  ❌ ES connection failed!"
    exit 1
fi

if curl -s http://localhost:8081/actuator/health >/dev/null 2>&1; then
    ENTRY_STATUS=$(curl -s http://localhost:8081/actuator/health | jq -r '.status' 2>/dev/null || echo "unknown")
    echo "  ✅ Entry Service connected (status: $ENTRY_STATUS)"
else
    echo "  ❌ Entry Service connection failed!"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Generating funnel data..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Run funnel generation
if "$SCRIPT_DIR/generate-full-funnel.sh" "$ACTIVITY_ID" "$NUM_VISITORS"; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ Funnel data generation completed!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ Funnel data generation failed!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
fi

# Verify data
echo ""
echo "🔍 Verifying data in ES..."
EVENT_COUNT=$(curl -s "http://localhost:9200/behavior-events/_count?q=properties.activityId:${ACTIVITY_ID}" | jq -r '.count' 2>/dev/null || echo "0")
echo "  📊 Total events for Activity $ACTIVITY_ID: $EVENT_COUNT"

if [ "$EVENT_COUNT" -gt 0 ]; then
    echo ""
    echo "  📈 Event breakdown:"
    curl -s -X POST "http://localhost:9200/behavior-events/_search" -H 'Content-Type: application/json' -d"{
        \"size\": 0,
        \"query\": {
            \"term\": {\"properties.activityId\": $ACTIVITY_ID}
        },
        \"aggs\": {
            \"by_type\": {
                \"terms\": {\"field\": \"triggerType\"}
            }
        }
    }" | jq -r '.aggregations.by_type.buckets[] | "    \(.key): \(.doc_count)"'
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 Done!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Cleanup port-forwards if we started them
if [ "$ES_ALREADY_RUNNING" = false ] && [ -n "$ES_PID" ]; then
    echo ""
    read -p "🛑 Stop ES port-forward? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kill $ES_PID 2>/dev/null && echo "  ✅ ES port-forward stopped"
    fi
fi

if [ "$ENTRY_ALREADY_RUNNING" = false ] && [ -n "$ENTRY_PID" ]; then
    read -p "🛑 Stop Entry Service port-forward? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kill $ENTRY_PID 2>/dev/null && echo "  ✅ Entry Service port-forward stopped"
    fi
fi

echo ""
echo "💡 Tip: 포트포워딩을 유지하려면 'n' 선택 후 다음 Activity 실행"
echo "    ./single-activity-funnel.sh 4 15000"
echo ""
