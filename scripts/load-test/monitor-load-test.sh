#!/bin/bash

# Axon FCFS Load Test Monitoring Script
# Usage: ./monitor-load-test.sh <ACTIVITY_ID>

ACTIVITY_ID=${1:-1}
echo "=========================================="
echo "🔍 Axon FCFS Load Test Monitoring"
echo "=========================================="
echo "Activity ID: $ACTIVITY_ID"
echo "Started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 1. Redis 카운터 초기값
echo "📊 Initial Redis Counter:"
kubectl exec axon-redis-master-0 -- redis-cli -a axon1234 GET "campaignActivity:${ACTIVITY_ID}:counter" 2>/dev/null | tr -d '\r\n' || echo "0"

# 2. DB 초기 카운트 (SSH tunnel 필요: 127.0.0.1:13306)
echo ""
echo "📊 Initial DB Count:"
mysql -h127.0.0.1 -P13306 -uaxon_user -paxon1234 axon_db -e "SELECT COUNT(*) as entry_count FROM campaign_activity_entries WHERE campaign_activity_id = ${ACTIVITY_ID};" 2>&1 | grep -v "Warning" | tail -1

echo ""
echo "=========================================="
echo "⏳ Waiting for load test..."
echo "Press Ctrl+C to see final results"
echo "=========================================="
echo ""

# 3. Core-service 로그 모니터링 (백그라운드)
echo "📋 Core-service logs (FCFS batch processing):"
kubectl logs -f -l app=core-service --tail=0 2>/dev/null | grep -E "(Processing FCFS batch|After deduplication|FCFS batch processed|Error processing batch)" &
CORE_LOG_PID=$!

# 4. Entry-service 로그 모니터링 (백그라운드)
echo ""
echo "📋 Entry-service logs (Payment confirm):"
kubectl logs -f -l app=entry-service --tail=0 2>/dev/null | grep -E "(Payment|Kafka.*publish)" &
ENTRY_LOG_PID=$!

# Cleanup on exit
trap "kill $CORE_LOG_PID $ENTRY_LOG_PID 2>/dev/null; echo ''; echo 'Monitoring stopped.'; exit 0" INT TERM

# Wait for user interrupt
wait
