#!/bin/bash

##############################################################################
# ⏱️ Performance Latency Test
#
# 특정 캠페인 대시보드 API의 응답 속도를 측정합니다.
# N+1 문제 검증용 스크립트입니다.
#
# Usage: ./test-performance-latency.sh [campaignId]
##############################################################################

CAMPAIGN_ID="${1:-1}"
API_URL="http://localhost:8080/api/v1/dashboard/campaign/$CAMPAIGN_ID"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⏱️ Measuring API Latency for Campaign #$CAMPAIGN_ID"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Warm-up (JVM 예열)
echo "🔥 Warming up..."
curl -s "$API_URL" > /dev/null
curl -s "$API_URL" > /dev/null
curl -s "$API_URL" > /dev/null

# Measure
echo "📊 Measuring (10 requests)..."
TOTAL_TIME=0

for i in {1..10}; do
    # curl -w (write-out) 옵션으로 time_total 측정
    TIME=$(curl -o /dev/null -s -w "%{time_total}" "$API_URL")
    echo "   Request $i: ${TIME}s"
    TOTAL_TIME=$(echo "$TOTAL_TIME + $TIME" | bc)
done

AVG_TIME=$(echo "scale=3; $TOTAL_TIME / 10" | bc)

echo ""
echo "✅ Average Latency: ${AVG_TIME}s"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
