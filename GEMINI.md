# Axon Project Context & Agent Guidelines

## 🤖 Agent Identity & Mandates
You are an intelligent **CLI Agent** with full terminal access. Your primary goal is to **solve problems autonomously** by leveraging your tools.

### Core Behaviors
1.  **Act, Don't Just Ask:**
    *   **Proactive Investigation:** If you need to know the server status, logs, or DB data, **run the command yourself**. Do not ask the user to do it for you unless it requires physical access or blocked permissions.
    *   **Tools:** Use `run_shell_command`, `read_file`, `list_directory` aggressively to gather context.

2.  **Scientific Debugging:**
    *   **Hypothesis:** Formulate a clear theory (e.g., "The DB lock is causing the timeout").
    *   **Verification:** Execute a command to prove/disprove it (e.g., `SHOW PROCESSLIST`, `grep` logs).
    *   **Conclusion:** Based on the output, move to the next step.

3.  **Codebase Mastery:**
    *   Read the code (`read_file`, `glob`) to understand the actual logic, not just the user's description.
    *   Verify configurations (`application.yml`, `configmap.yaml`) before assuming they are correct.

---

# Axon Project Context

## Project Overview
**Axon** is a high-traffic event processing system designed to handle First-Come-First-Served (FCFS) campaign activities. It utilizes a Microservices Architecture (MSA) to separate high-throughput ingress traffic from core business logic.

### Key Technologies
*   **Language:** Java 21
*   **Framework:** Spring Boot 3.x
*   **Infrastructure:** Kubernetes (K2P), Docker
*   **Messaging:** Apache Kafka (KRaft mode)
*   **Database:** MySQL 8.0, Redis (Cluster/Standalone)
*   **Search/Log:** Elasticsearch, Fluent Bit, Kibana
*   **Monitoring:** Prometheus, Grafana, Spring Actuator

## Architecture

### 1. Entry Service (Ingress)
*   **Role:** Handles high-concurrency user traffic (Traffic shaping, Token validation, Redis FCFS).
*   **Key Logic:**
    *   **FCFS:** Uses Redis `INCR` to manage event limits atomically.
    *   **Kafka Producer:** Sends `CampaignActivityKafkaProducerDto` to `axon.campaign-activity.command` topic upon success.
    *   **Payment Proxy:** Forwards payment requests to Kafka or handles them via Redis tokens.

### 2. Core Service (Business Logic)
*   **Role:** Consumes Kafka messages, persists data to MySQL, and handles business rules (Purchase, UserSummary).
*   **Key Logic:**
    *   **Consumer:** `CampaignActivityConsumerService` reads from Kafka.
    *   **Strategy:** `FirstComeFirstServeStrategy` -> `upsertBatch`.
    *   **Persistence:** `CampaignActivityEntryService` (Entry DB) -> `PurchaseHandler` (Purchase DB).
    *   **Resilience:** Implements **Batch Fallback** logic. If a batch fails (e.g., `DuplicateKeyException`), it retries items individually (`retryIndividually`) to prevent data loss. Handles `UnexpectedRollbackException` to avoid zombie transactions.

### 3. Common Messaging
*   Shared DTOs and Enums (e.g., `CampaignActivityType`, `CampaignActivityKafkaProducerDto`).

## Key Flows & Mechanisms

### FCFS Traffic Flow
1.  **User** requests Entry (`POST /api/v1/entries`).
2.  **Entry Service** checks Redis Counter (`INCR`).
    *   If `<= Limit`: Produce Kafka Message.
    *   If `> Limit`: Return 410 Gone (and `DECR`/Restore stock).
3.  **Core Service** consumes Kafka Message.
4.  **Batch Insert:** `CampaignActivityEntry` is saved to MySQL (Bulk).
5.  **Event Publishing:** `PurchaseInfoDto` event is published.
6.  **Purchase Handler:**
    *   Buffers events.
    *   Flushes batch to DB (`TransactionTemplate`).
    *   **Performance Optimization:** DB Stock decrease (`products` table) and UserSummary update (`user_summary` table) are currently **disabled (commented out)** to prevent DB lock contention during high-load tests.
    *   **Fallback:** If batch fails, retries individually.

### Load Testing (k6)
*   **Preparation:** `scripts/load-test/prepare-load-test.sh`
    *   Generates `jwt-tokens.json`.
    *   Resets DB/Redis data (`CLEANUP`).
    *   Ensures `UserSummary` exists.
*   **Execution:** `scripts/load-test/k6-fcfs-load-test.js`
    *   Scenarios: `spike`, `constant`, `stress`.
    *   Metrics: `fcfs_success_count`, `payment_confirm_success`.

## Operational Commands

### Local Development
```bash
# Build
./gradlew clean build -x test

# Run Dependencies
docker-compose up -d

# Run Services (IntelliJ or Terminal)
java -jar core-service/build/libs/*.jar
java -jar entry-service/build/libs/*.jar
```

### Kubernetes (K2P) Deployment
```bash
# Apply Configs
kubectl apply -f k8s/core-service/configmap.yaml
kubectl apply -f k8s/entry-service/configmap.yaml

# Restart Pods
kubectl rollout restart deployment/core-service
kubectl rollout restart deployment/entry-service

# Check Logs
kubectl logs -l app=core-service --tail=200 -f
```

### Troubleshooting
*   **DB Lock:** Check `SHOW PROCESSLIST` and `information_schema.innodb_trx`. Kill zombie sessions (`SLEEP` > 1000s) if necessary.
*   **ES Connection:** Ensure `configmap.yaml` points to `http://elasticsearch-master:9200`.
*   **Redis Over-booking:** FCFS logic relies on Redis atomicity but allows slight over-booking in high-concurrency race conditions (Act-then-Check).
*   **Data Mismatch:** If `Entry` > `Purchase`, check `Core-Service` logs for `Error processing purchase batch` or `Individual save failed`.