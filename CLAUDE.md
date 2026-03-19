# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`plantogether-destination-service` is a Spring Boot 3.3.6 microservice (Java 21) that manages collective travel destination proposals and voting within the PlanTogether platform.

## Build & Run Commands

```bash
# Build
mvn clean package

# Run locally
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Build Docker image
docker build -t plantogether-destination-service .

# Run Docker container
docker run -p 8083:8083 \
  -e KEYCLOAK_URL=http://host.docker.internal:8180 \
  -e DB_HOST=host.docker.internal \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e RABBITMQ_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  plantogether-destination-service
```

## Architecture

This service is part of a microservices ecosystem and integrates with:

- **Keycloak** (port 8180) — OAuth2/JWT authentication; roles extracted from `realm_access.roles` JWT claim
- **PostgreSQL** — persistence via Spring Data JPA; schema managed by Flyway (`src/main/resources/db/migration/`)
- **RabbitMQ** — event-driven messaging; publishes `DestinationProposed`, `DestinationVoted`, `DestinationWon`; consumes `TripCreated`, `MemberJoined`
- **Redis** — caching for destination rankings
- **MinIO** — object storage for destination photos (max 10MB)
- **Eureka Server** (port 8761) — service discovery
- **Spring Cloud Config** — centralized configuration

### Domain Model (planned)

- **Destination** — UUID, trip_id, title, description, photo_key, budget, coordinates, proposed_by, timestamps
- **DestinationVote** — id, destination_id, keycloak_id, vote_type, vote_value, voted_at
- **DestinationComment** — id, destination_id, keycloak_id, content, created_at

Voting modes: `SIMPLE` (one choice), `APPROVAL` (multiple choices), `RANKING` (Borda scoring).

### Security

- Stateless JWT auth via `KeycloakJwtConverter` — maps Keycloak realm roles to `ROLE_` prefixed Spring authorities, uses JWT subject as principal name
- Method-level security enabled (`@EnableMethodSecurity`)
- Public endpoints: `/actuator/health`, `/actuator/info`; all others require authentication
- CSRF disabled (stateless REST API)

### Shared Library

Uses `com.plantogether:plantogether-common:1.0.0-SNAPSHOT` for shared exception types and `ErrorResponse` formatting used in `GlobalExceptionHandler`.

## Key Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `plantogether` | DB username |
| `DB_PASSWORD` | `plantogether` | DB password |
| `KEYCLOAK_URL` | `http://localhost:8180` | Keycloak base URL |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `RABBITMQ_USER` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka registration URL |

## Development Status

The project is in early bootstrap phase. The infrastructure is wired up (security, exception handling, config), but the following layers are empty and pending implementation:

- `controller/` — REST endpoints
- `service/` — business logic
- `model/` — JPA entities
- `repository/` — Spring Data JPA interfaces
- `dto/` — request/response DTOs
- `config/` — additional Spring beans
- `db/migration/V1__init_schema.sql` — database schema
