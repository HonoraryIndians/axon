# Repository Guidelines

## Project Structure & Module Organization
- **core-service/** – Spring Boot application implementing campaign logic, purchase handling, and event occurrences.  
  - `src/main/java/` – business modules (service, domain, repository, event).  
  - `src/test/java/` – unit/integration tests.  
- **entry-service/** – lightweight ingestion service that publishes Kafka events.  
- **common-messaging/** – shared DTOs, enums, and Kafka topic constants.  
- **docs/** – reference documents (e.g., `purchase-event-flow.md`).  
- Infrastructure assets (Docker Compose, Kafka init scripts) live in the repository root.

## Build, Test, and Development Commands
- `./gradlew build` – compiles all modules and runs tests.  
- `./gradlew :core-service:test` / `:entry-service:test` – module-specific test execution.  
- `docker-compose up -d` – spins up Kafka, MySQL, Redis, etc., for local development.  
- `./gradlew bootRun` (in module directories) – launches each Spring Boot service locally.

## Coding Style & Naming Conventions
- Java code follows standard Spring conventions: 4-space indentation, UpperCamelCase classes, lowerCamelCase methods/fields.  
- Package organization: `service`, `controller`, `domain`, `repository`, `event`.  
- DTOs and enums reside in `common-messaging`.  
- Prefer constructor injection (Lombok `@RequiredArgsConstructor`).  
- Apply builder patterns for immutable DTOs/entities when practical.

## Testing Guidelines
- JUnit 5 + Mockito for unit tests, SpringBootTest for integration tests.  
- Name tests with descriptive verbs (`CampaignActivityConsumerServiceTest.decreaseStock_ConcurrencyTest`).  
- Ensure tests run idempotently (clean DB state via repository deletes or transactional rollbacks).

## Commit & Pull Request Guidelines
- Follow Conventional Commit style: `feat:`, `fix:`, `chore:` etc.  
  - Example: `feat(purchase): emit approval events and log purchase occurrences`.  
- Pull requests should include: brief summary, linked issue (if any), testing evidence/commands run, screenshots when UI changes apply.  
- Keep PRs scoped; large changes should be split into logical commits.

