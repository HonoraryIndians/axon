#!/bin/bash
set -ex

echo "Waiting for Kafka broker..."
for i in {1..30}; do
  nc -z broker_1 9092 && echo "Kafka broker is ready!" && break
  echo "Kafka not ready yet... ($i/30)"
  sleep 3
done

echo "Creating Kafka topics..."
TOPICS=(
  "axon.campaign-activity.command"
  "axon.campaign-activity.log"
  "axon.event.raw"
  "axon.user.login"
)

for TOPIC in "${TOPICS[@]}"; do
  kafka-topics --create --if-not-exists --topic "$TOPIC" --bootstrap-server broker_1:29092 --partitions 1 --replication-factor 1
done
kafka-topics --list --bootstrap-server broker_1:29092

echo "Kafka topics created successfully!"
