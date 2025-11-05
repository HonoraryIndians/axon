# Project Task Plan (5 Weeks Remaining)

| Module / Feature | Description | Deadline | Owner(s) | Notes |
| --- | --- | --- | --- | --- |
| **Event Instrumentation Backend** | Expose API that delivers active event definitions/trigger conditions to JS snippet; ensure definitions resolved from `events` table. | Week 1 (YYYY-MM-DD) | Dev A | Include fallback for missing definitions; align DTOs with `common-messaging`. |
| **JavaScript Snippet Integration** | Implement JS snippet to listen for URL/behavior triggers, post EventOccurrence to entry-service, forward to Kafka (temporary). | Week 1-2 | Dev B | Plan future migration to Fluentd/Filebeat; document integration steps. |
| **Event Occurrence Pipeline Enhancements** | Extend entry-service → Kafka → core-service flow; ensure occurrence stored via `EventOccurrenceService` and customizable strategies (e.g., Purchase). | Week 2 | Dev A | Validate triggered events with mocks and real requests. |
| **Campaign Activity Condition Engine** | Implement backend condition checks (e.g., `last_purchase_at` required) for campaign participation; integrate with summary service. | Week 3 | Dev B | Reuse `UserSummary` data; add tests for edge conditions. |
| **Dashboard Foundations** | Develop API(s) to aggregate campaign activity/user behavior for dashboard (initial metrics). | Week 3-4 | Dev A | Focus on minimal viable metrics for portfolio demo. |
| **Load/Performance Testing** | Prepare load scripts (e.g., JMeter/Gatling), run tests on campaign entry flow, analyze bottlenecks. | Week 4 | Dev A, Dev B | Record throughput/latency; baseline for k8s deployment. |
| **Performance Optimization & Hardening** | Apply fixes from load testing (DB indices, caching, tuning). | Week 4-5 | Dev A, Dev B | Document before/after results; ensure reproducible steps. |
| **Deployment Prep (k8s)** | Containerize services, prepare Helm/manifest files, configure Kafka/Redis pods, outline RDS migration strategy. | Week 5 | Dev A | Provide README for k8s setup; highlight AWS RDS future plan. |
| **Documentation & Portfolio Packaging** | Compile architecture and flow docs (e.g., `purchase-event-flow.md`), summarize learnings, screenshots for dashboard/demo. | Week 5 | Dev B | Emphasize school/company collaboration; ready material for portfolio. |

> **Guiding Principles**
> - Focus on demonstrable features and documentation to strengthen portfolio impact.  
> - Prioritize backend robustness (event handling, data integrity) while enabling future frontend/dashboard work.  
> - Actively track task completion and adjust deadlines as risk/issues arise.
