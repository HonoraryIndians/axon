# Dashboard Testing Scripts

Quick reference for testing the Activity-level dashboard with real data.

## 📋 Prerequisites

Before testing the dashboard, ensure these services are running:

```bash
# 1. Start infrastructure
docker-compose up -d  # MySQL, Redis, Kafka, Elasticsearch

# 2. Start Entry-service (port 8081)
cd ../entry-service && ./gradlew bootRun

# 3. Start Core-service (port 8080)
cd ../core-service && ./gradlew bootRun
```

## 🧪 Testing Workflow

### Step 1: Verify Current Data

Check if you have existing data for your activity:

```bash
./scripts/verify-dashboard-data.sh 1  # Replace 1 with your activityId
```

**Expected Output:**
```
✅ Good! You have frontend events (PAGE_VIEW, CLICK)
✅ Great! You have backend PURCHASE events
```

### Step 2: Generate Test Data (if needed)

If you don't have enough data, generate test events:

```bash
# Generate events for activity ID 1 with 50 simulated users
./scripts/generate-test-events.sh 1 50
```

**What this creates:**
- 50 PAGE_VIEW events (100% of users)
- 20 CLICK events (40% conversion)
- Backend events (APPROVED, PURCHASE) require actual FCFS flow

### Step 3: Test REST API

Verify the dashboard API returns data:

```bash
# Pretty-print JSON
curl -s http://localhost:8080/api/v1/dashboard/activity/1 | jq '.'
```

**Expected Response:**
```json
{
  "campaignActivityId": 1,
  "period": "LAST_7_DAYS",
  "timestamp": "2025-11-19T...",
  "overview": {
    "totalVisits": 50,
    "totalClicks": 20,
    "approvedCount": 6,
    "purchaseCount": 4
  },
  "funnel": [
    { "stepName": "VISIT", "count": 50, "conversionRate": 100.0 },
    { "stepName": "CLICK", "count": 20, "conversionRate": 40.0 },
    { "stepName": "APPROVED", "count": 6, "conversionRate": 30.0 },
    { "stepName": "PURCHASE", "count": 4, "conversionRate": 66.7 }
  ],
  "realtime": {
    "activity": {
      "activeParticipants": 12,
      "remainingStock": 44
    }
  }
}
```

### Step 4: Test SSE Endpoint

Test real-time updates:

```bash
# Stream dashboard updates (Ctrl+C to stop)
curl -N http://localhost:8080/api/v1/dashboard/stream/activity/1
```

**Expected Output (every 5 seconds):**
```
event:dashboard-update
data:{"campaignActivityId":1,"overview":{...},"funnel":[...],"realtime":{...}}

event:dashboard-update
data:{"campaignActivityId":1,"overview":{...},"funnel":[...],"realtime":{...}}
...
```

### Step 5: Test in Browser

Open Chrome DevTools → Network tab, then visit:

```
http://localhost:8080/api/v1/dashboard/stream/activity/1
```

You should see:
- Connection type: `text/event-stream`
- Status: `200 OK` (pending)
- Data streaming continuously

## 📊 Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Test Script                                                 │
│ (generate-test-events.sh)                                   │
└────────────────┬────────────────────────────────────────────┘
                 ▼
         POST /api/v1/behavior/events
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Entry-service (8081)                                        │
│ - Validates event                                           │
│ - Publishes to Kafka: axon.event.raw                        │
└────────────────┬────────────────────────────────────────────┘
                 ▼
         Kafka Topic: axon.event.raw
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Kafka Connect                                               │
│ - Elasticsearch Sink Connector                              │
│ - Writes to behavior-events index                           │
└────────────────┬────────────────────────────────────────────┘
                 ▼
         Elasticsearch: behavior-events
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Core-service Dashboard API                                  │
│ - DashboardService.getDashboardByActivity()                 │
│ - Queries ES for PAGE_VIEW, CLICK, PURCHASE                 │
│ - Queries MySQL for APPROVED                                │
│ - Queries Redis for realtime metrics                        │
└────────────────┬────────────────────────────────────────────┘
                 ▼
         Dashboard Response (JSON)
                 ▼
         Frontend (Thymeleaf + Chart.js)
```

## 🔧 Troubleshooting

### No events in Elasticsearch?

1. Check Kafka Connect is running:
   ```bash
   curl http://localhost:8083/connectors
   ```

2. Check connector status:
   ```bash
   curl http://localhost:8083/connectors/elasticsearch-sink-behavior-events/status
   ```

3. Check Kafka messages:
   ```bash
   docker exec -it axon-kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic axon.event.raw \
     --from-beginning \
     --max-messages 5
   ```

### Dashboard returns empty data?

1. Verify Elasticsearch has data:
   ```bash
   curl http://localhost:9200/behavior-events/_count
   ```

2. Check if events match your activity ID:
   ```bash
   curl -X POST http://localhost:9200/behavior-events/_search \
     -H "Content-Type: application/json" \
     -d '{"query":{"wildcard":{"pageUrl":"*activity/1*"}}, "size":5}'
   ```

3. Check Core-service logs for errors:
   ```bash
   # Look for BehaviorEventService or DashboardService errors
   tail -f core-service/logs/application.log | grep -i error
   ```

### SSE connection drops immediately?

1. Check DashboardController logs:
   ```bash
   # Should see: "SSE connection opened for activity: X"
   tail -f core-service/logs/application.log | grep SSE
   ```

2. Verify activity exists in database:
   ```sql
   SELECT * FROM campaign_activities WHERE id = 1;
   ```

## 📚 Next Steps

After verifying data flow:

1. ✅ Data generation scripts work
2. ✅ REST API returns correct data
3. ✅ SSE streams real-time updates
4. 🚀 Ready to build Thymeleaf dashboard UI!

See main project README for frontend implementation guide.
