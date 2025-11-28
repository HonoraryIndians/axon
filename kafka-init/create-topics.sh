#!/bin/bash
set -e

echo "Waiting for Kafka broker..."
for i in {1..30}; do
  nc -z broker_1 9092 && echo "Kafka broker is ready!" && break
  echo "Kafka not ready yet... ($i/30)"
  sleep 3
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Creating Axon CDP Kafka Topics (Simplified)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 1. event.raw - All behavior events (analytics)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "Creating topic: axon.event.raw"
echo "  Purpose: All behavior events (PAGE_VIEW, CLICK, APPROVED, PURCHASE, LOGIN)"
echo "  Flow: Frontend/Backend → Kafka → Kafka Connect → Elasticsearch"

kafka-topics --create --if-not-exists \
  --topic "axon.event.raw" \
  --bootstrap-server broker_1:29092 \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=604800000 \
  --config compression.type=lz4

echo "  ✅ Created: axon.event.raw (1 partition - local dev)"
echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 2. campaign-activity.command - CQRS commands (business logic)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo "Creating topic: axon.campaign-activity.command"
echo "  Purpose: CQRS commands for Entry/Purchase creation"
echo "  Flow: Entry-service → Kafka → Core-service @KafkaListener"

kafka-topics --create --if-not-exists \
  --topic "axon.campaign-activity.command" \
  --bootstrap-server broker_1:29092 \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000 \
  --config compression.type=lz4

echo "  ✅ Created: axon.campaign-activity.command (1 partition - local dev)"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ All Kafka topics created successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "📋 Topic List:"
kafka-topics --list --bootstrap-server broker_1:29092
echo ""
