#!/bin/bash
set -ex

echo "Waiting for Kafka broker..."
for i in {1..30}; do
  nc -z broker_1 9092 && echo "Kafka broker is ready!" && break
  echo "Kafka not ready yet... ($i/30)"
  sleep 3
done

echo "Creating Kafka topics..."
kafka-topics --create --if-not-exists --topic event --bootstrap-server broker_1:9092 --partitions 1 --replication-factor 1
kafka-topics --create --if-not-exists --topic log --bootstrap-server broker_1:9092 --partitions 1 --replication-factor 1
kafka-topics --list --bootstrap-server broker_1:9092

echo "Kafka topics created successfully!"
