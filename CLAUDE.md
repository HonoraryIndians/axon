# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Axon CDP is a multi-module Spring Boot ecosystem implementing real-time campaign intelligence with FCFS (First Come First Serve) promotions. The architecture separates concerns between an entry validation layer and a domain processing core, connected via Kafka messaging.

### Key Architecture Components

**Entry-service (port 8081)**: Lightweight validation and reservation service
- Handles FCFS slot reservations via Redis counters and sets
- Publishes events to Kafka topics: `axon.event.raw`, `axon.campaign-activity.command`
- Implements fast validation using Redis-cached user data
- Issues reservation tokens for payment flows

**Core-service (port 8080)**: Domain logic and persistence engine  
- Consumes Kafka commands to process campaign activities
- Manages MySQL persistence for campaigns, entries, users, productse
- Handles complex validation rules and user summary updates
- Serves Thymeleaf UI with auto-injected behavior tracking

**Data Flow**: `Browser (JS tracker) → Entry-service (Redis FCFS) → Kafka → Core-service (MySQL) + Kafka Connect → Elasticsearch`

### Critical Kafka Topics
- `axon.event.raw`: Raw behavior events from JS tracker
- `axon.campaign-activity.command`: FCFS reservation commands
- `axon.campaign-activity.log`: Domain event logs
- `axon.user.login`: User authentication events

## Development Commands

### Infrastructure Setup
```bash
# Start all services (Kafka, MySQL, Redis, Elasticsearch, Kafka Connect)
docker-compose up -d

# Check service status
docker-compose ps
```

### Build & Test Commands
```bash
# Build all modules
./gradlew build

# Module-specific builds (each service has its own gradlew)
cd core-service && ./gradlew build --no-daemon
cd entry-service && ./gradlew build --no-daemon

# Run specific tests
cd core-service && ./gradlew test --tests="CampaignActivityConsumerServiceTest"
cd entry-service && ./gradlew test --tests="EntryControllerTest"

# Run services locally
cd core-service && ./gradlew bootRun
cd entry-service && ./gradlew bootRun
```

### Debugging Infrastructure
```bash
# Check database tables
docker exec axon-mysql mysql -u axon_user -paxon_password axon_db -e "SHOW TABLES;"

# Redis connection test
redis-cli -h localhost -p 6379 ping

# Elasticsearch health
curl http://localhost:9200/_cluster/health

# Kafka Connect status
curl http://localhost:8083/connectors
```

## Key Implementation Details

### Redis FCFS Implementation
- Participant tracking: Redis Sets with key pattern `campaignActivity:{id}:participants`
- Counter management: Redis counters for order determination
- Reservation tokens: Temporary reservations with TTL for payment flows

### Kafka Serialization
- Producer: `JsonSerializer` with type headers enabled
- Consumer: `JsonDeserializer` with trusted packages set to "*"
- Shared DTOs defined in `common-messaging` module

### Database Configuration
- **Core-service**: MySQL on localhost:3306, database `axon_db`
- **Both services**: Redis on localhost:6379 for caching and FCFS logic
- **Hibernate**: DDL auto-create in development (change to 'validate' for production)

## Critical Integration Points

### Behavior Tracker JavaScript
- Auto-injected into Thymeleaf templates from `core-service/src/main/resources/static/js/behavior-tracker.js`
- Sends events to Entry-service `/api/v1/behavior/events` endpoint
- Configuration via `axon-tracker-config.js` for token/user providers

### Kafka Connect Pipeline
- Elasticsearch Sink connectors: `elasticsearch-sink-behavior-events`, `elasticsearch-sink-connector`
- Maps Kafka topics to ES indices: `behavior-events`, `axon.event.raw`
- Handles schema-less JSON with automatic index creation

### Service Communication
- Entry-service → Core-service: Via Kafka asynchronous messaging only
- JWT tokens shared between services (secret: configured in application.yml)
- OAuth2 integration for user authentication (profiles include 'oauth')

## Testing Notes

- Integration tests require Docker Compose stack to be running
- Known flaky test: `CampaignActivityConsumerServiceTest` concurrency test may fail due to race conditions
- Tests use `@SpringBootTest` with test-specific Redis/MySQL cleanup
- Mock external dependencies in unit tests; use TestContainers for integration tests if needed

## Documentation References

- Architecture flows: `docs/purchase-event-flow.md`, `docs/campaign-activity-limit-flow.md`
- Behavior tracking: `docs/behavior-tracker.md`, `docs/snippets/behavior-tracker.js`
- Analytics pipeline: `docs/behavior-event-fluentd-plan.md`
- Dashboard specifications: `docs/marketing-dashboard-spec.md`
- Project roadmap: `docs/project-tasks.md`