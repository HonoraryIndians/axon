#!/bin/bash

# Axon FCFS Load Test Result Checker
# Usage: ./check-results.sh <ACTIVITY_ID>

ACTIVITY_ID=${1:-1}

echo ""
echo "=========================================="
echo "📊 Load Test Results Comparison"
echo "=========================================="
echo "Activity ID: $ACTIVITY_ID"
echo "Checked at: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 1. Redis 카운터 (FCFS 성공 카운트)
echo "🔴 Redis Counter (FCFS success count):"
REDIS_COUNT=$(kubectl exec axon-redis-master-0 -- redis-cli -a axon1234 GET "campaignActivity:${ACTIVITY_ID}:counter" 2>/dev/null | tr -d '\r\n')
echo "   $REDIS_COUNT"

# 2. Redis Set 크기 (중복 없는 참여자 수)
echo ""
echo "🔴 Redis Set Size (unique participants):"
REDIS_SET_SIZE=$(kubectl exec axon-redis-master-0 -- redis-cli -a axon1234 SCARD "campaignActivity:${ACTIVITY_ID}:participants" 2>/dev/null | tr -d '\r\n')
echo "   $REDIS_SET_SIZE"

# 3. DB Entry 카운트 (실제 저장된 수)
echo ""
echo "💾 DB Entry Count (actual saved):"
DB_COUNT=$(mysql -h127.0.0.1 -P13306 -uaxon_user -paxon1234 axon_db -e "SELECT COUNT(*) FROM campaign_activity_entries WHERE campaign_activity_id = ${ACTIVITY_ID};" 2>&1 | grep -v "Warning" | tail -1)
echo "   $DB_COUNT"

# 4. 차이 계산
echo ""
echo "=========================================="
echo "📈 Analysis:"
echo "=========================================="

if [[ "$REDIS_COUNT" =~ ^[0-9]+$ ]] && [[ "$DB_COUNT" =~ ^[0-9]+$ ]]; then
    DIFF=$((REDIS_COUNT - DB_COUNT))
    echo "Redis Counter:     $REDIS_COUNT"
    echo "DB Entries:        $DB_COUNT"
    echo "Difference:        $DIFF (missing entries)"

    if [ "$DIFF" -eq 0 ]; then
        echo "✅ Perfect match! No data loss."
    elif [ "$DIFF" -gt 0 ]; then
        echo "⚠️  ${DIFF} entries missing in DB!"
        echo ""
        echo "Possible causes:"
        echo "  1. Deduplication in processBatch() (same userId requested multiple times)"
        echo "  2. Kafka message processing errors"
        echo "  3. DB transaction conflicts"
        echo ""
        echo "Check Core-service logs for:"
        echo "  - 'After deduplication: X messages (removed Y duplicates)'"
        echo "  - 'Error processing batch'"
    else
        echo "❌ DB has MORE entries than Redis counter? (impossible)"
    fi
else
    echo "⚠️  Could not calculate difference (invalid numbers)"
fi

# 5. Redis Set vs DB 비교 (중복 체크)
echo ""
if [[ "$REDIS_SET_SIZE" =~ ^[0-9]+$ ]] && [[ "$DB_COUNT" =~ ^[0-9]+$ ]]; then
    SET_DIFF=$((REDIS_SET_SIZE - DB_COUNT))
    echo "Redis Set Size:    $REDIS_SET_SIZE (unique users)"
    echo "DB Entries:        $DB_COUNT"
    echo "Difference:        $SET_DIFF"

    if [ "$SET_DIFF" -eq 0 ]; then
        echo "✅ Redis Set matches DB (no duplicates in Redis)"
    elif [ "$SET_DIFF" -gt 0 ]; then
        echo "⚠️  Redis Set has more unique users than DB"
    fi
fi

echo ""
echo "=========================================="
