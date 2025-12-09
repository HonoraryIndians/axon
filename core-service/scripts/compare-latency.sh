ƒ#!/bin/bash

##############################################################################
# 📊 Compare Latency: Legacy vs Optimized
#
# 구식 API(Loop)와 신식 API(Aggregation)의 응답 속도를 비교합니다.
# N+1 문제 해결 효과를 검증하기 위한 스크립트입니다.
#
# Usage: ./compare-latency.sh [campaignId]
##############################################################################

CAMPAIGN_ID="${1:-1}"
URL_LEGACY="http://localhost:8080/api/v1/dashboard/campaign/$CAMPAIGN_ID"
URL_OPTIMIZED="http://localhost:8080/api/v1/dashboard/campaign/$CAMPAIGN_ID/optimized"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Performance Benchmark: Campaign #$CAMPAIGN_ID"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Warm-up
echo "🔥 Warming up JVM..."
curl -s "$URL_LEGACY" > /dev/null
curl -s "$URL_OPTIMIZED" > /dev/null

# Function to measure average latency
measure() {
    local URL=$1
    local TOTAL=0
    local COUNT=5 

    for i in $(seq 1 $COUNT); do
        TIME=$(curl -o /dev/null -s -w "%{time_total}" "$URL")
        TOTAL=$(echo "$TOTAL + $TIME" | bc)
    done
    
    echo "scale=4; $TOTAL / $COUNT" | bc
}

echo "   Testing Legacy (Loop)..."
AVG_LEGACY=$(measure "$URL_LEGACY")
echo "   -> Result: ${AVG_LEGACY}s"

echo "   Testing Optimized (Agg)..."
AVG_OPTIMIZED=$(measure "$URL_OPTIMIZED")
echo "   -> Result: ${AVG_OPTIMIZED}s"

# Calculate Improvement
if (( $(echo "$AVG_OPTIMIZED > 0" | bc -l) )); then
    IMPROVEMENT=$(echo "scale=2; $AVG_LEGACY / $AVG_OPTIMIZED" | bc)
else
    IMPROVEMENT="Inf"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🏆 Benchmark Results"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   🐢 Legacy:    ${AVG_LEGACY}s"
echo "   🚀 Optimized: ${AVG_OPTIMIZED}s"
echo ""
echo "   🎉 Speedup:   ${IMPROVEMENT}x Faster!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"