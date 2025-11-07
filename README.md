# Axon Campaign Intelligence Platform

Axon is a multi-module Spring ecosystem that powers campaign orchestration, FCFS style promotions, and fine-grained behavior tracking for the Axon commerce stack. The repo hosts both runtime services and the documentation/operational assets that glue them together.

## Repository Layout

| Path | Description |
| --- | --- |
| `core-service/` | Primary Spring Boot app that owns campaign logic, purchase workflows, MySQL persistence, and async consumers. |
| `entry-service/` | Lightweight ingestion edge that validates requests, enforces FCFS via Redis, and publishes behavior/campaign events to Kafka. |
| `common-messaging/` | Shared DTOs, enums, and Kafka topic constants used by the services. |
| `docs/` | Reference material (behavior tracker spec, Fluentd/Filebeat plan, dashboard spec, etc.). |
| `docker-compose.yml`, `kafka-init/`, `fluentd/`, `k8s/` | Local infra + deployment assets (Kafka, MySQL, Redis, Fluentd sidecars, K8s manifests). |

## Highlights

- **Deterministic FCFS pipeline** – Entry-service performs segment filtering + Redis-based slot reservation so Kafka already carries ordered winners, while core-service focuses on domain effects (stock decrement, approvals, user summary updates).
- **Behavior tracking JS snippet** – `behavior-tracker.js` is auto-injected via Thymeleaf, fetches active trigger rules, captures page views/clicks, and streams events into `axon.event.raw` for downstream ES/DWH analytics.
- **Composable messaging** – Kafka topics (command + raw behavior) are the backbone; Fluentd/Filebeat gateways allow shipping behavior logs into Elasticsearch/K2P Logging without touching MySQL.
- **Documentation-first** – Flow docs (`docs/purchase-event-flow.md`, `docs/campaign-activity-limit-flow.md`, `docs/behavior-event-fluentd-plan.md`, `docs/marketing-dashboard-spec.md`) keep business and infra context close to the code.

## Architecture Overview

```
Browser (behavior-tracker.js)
        │ page views / clicks + entry requests
        ▼
Entry-service (Thymeleaf UI + controllers)
        │ Redis FCFS + validation
        ├── Kafka axon.campaign-activity.command
        └── Kafka axon.event.raw
                         │
                         ▼
              ┌──────────────────────┐
              │ core-service         │
              │  - Consumer          │
              │  - Domain services   │
              └─────────┬────────────┘
                        │
        ┌──────────────▼──────────────┐
        │ MySQL (campaigns/entries)   │
        │ Redis (counters/cache)      │
        └──────────────┬──────────────┘
                        │
        ┌──────────────▼──────────────┐
        │ Fluentd/Filebeat → ES/K2P   │
        │ (behavior analytics)        │
        └─────────────────────────────┘
```

## Local Development

1. **Prerequisites**
   - Java 17+
   - Docker + Docker Compose
   - Make sure `./gradlew` is executable (`chmod +x gradlew` if needed).

2. **Spin up infra**
   ```bash
   docker-compose up -d
   ```
   This supplies Kafka, ZooKeeper, MySQL, Redis, and any supporting containers defined in `docker-compose.yml`.

3. **Run services**
   - Entry-service UI / ingestion
     ```bash
     ./gradlew :entry-service:bootRun
     ```
   - Core-service domain engine
     ```bash
     ./gradlew :core-service:bootRun
     ```
   The behavior tracker static assets live under `core-service/src/main/resources/static/js`. When the entry UI renders via Thymeleaf, the tracker script is injected automatically.

4. **Helpful commands**
   - Build & test everything: `./gradlew build`
   - Module-specific tests: `./gradlew :core-service:test` or `:entry-service:test`
   - Consume Kafka topics for debugging: use `kafka-console-consumer` against `axon.campaign-activity.command` or `axon.event.raw`.

## Behavior Tracker Integration

- Authoritative spec: `docs/behavior-tracker.md`
- Snippet source for reference or CDN publishing: `docs/snippets/behavior-tracker.js`
- Entry-service provides:
  - `GET /api/v1/events/active` (active trigger definitions)
  - `POST /api/v1/behavior/events` (collect endpoint that writes to Kafka)
- Configure `tokenProvider`, `userIdProvider`, etc., per the doc. Toggle `debug=true` to view `[AxonBehaviorTracker]` logs in the browser console during QA.

## Analytics & Logging Plan

- `docs/behavior-event-fluentd-plan.md` – outlines Kafka → Fluentd/Filebeat → Elasticsearch ingestion for behavior logs, with K2P Logging deployment notes.
- `docs/marketing-dashboard-spec.md` – dashboard wireframe spec (KPI cards, funnel charts, cost/ROI panels) for the marketing team.
- Operational flows such as purchase events, campaign limits, and Redis FCFS handoffs are captured in the respective docs under `docs/`.

## Testing

- Unit & slice tests live under each module’s `src/test/java`.
- Integration tests rely on the Docker services; ensure `docker-compose` stack is running if a test references Kafka/MySQL.
- Gradle wrapper lock: if CI encounters the known wrapper lock issue, remove the stale `.gradle` lock file manually before rerunning (documented in `docs/project-tasks.md`).

## Contributing & Conventions

- Follow Conventional Commits (`feat:`, `fix:`, `chore:` …).
- Java code style: Spring defaults (4-space indent, constructor injection via Lombok `@RequiredArgsConstructor`, DTOs/enums in `common-messaging`).
- Tests must be deterministic—cleanup DB state or use transactional rollbacks.
- When adding new flows, update/author docs in `docs/` so the pipeline remains discoverable.

Happy shipping! Reach out via AGENTS.md instructions if additional automation or infra changes are needed.
