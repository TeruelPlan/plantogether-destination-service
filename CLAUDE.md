# CLAUDE.md

This file provides guidance to Claude when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Docker build
docker build -t plantogether-destination-service .
docker run -p 8083:8083 -p 9083:9083 \
  -e DB_HOST=host.docker.internal \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e RABBITMQ_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  plantogether-destination-service
```

**Prerequisites:**

Local Maven builds resolve shared libs (`plantogether-parent`, `plantogether-bom`, `plantogether-common`,
`plantogether-proto`) from GitHub Packages. Export a PAT with `read:packages` before running `mvn`:

```bash
export GITHUB_ACTOR=<your-github-username>
export PACKAGES_TOKEN=<your-PAT-with-read:packages>
mvn -s .settings.xml clean package
```

## Architecture

Spring Boot 3.5.9 microservice (Java 21). Manages destination proposals, voting, and comments for trips.

**Ports:** REST `8083` · gRPC `9083` (server — reserved for future consumers)

**Package:** `com.plantogether.destination`

### Package structure

```
com.plantogether.destination/
├── config/          # RabbitConfig
├── controller/      # REST controllers
├── domain/          # JPA entities (Destination, DestinationVote, DestinationComment)
├── repository/      # Spring Data JPA
├── service/         # Business logic
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (IsMember → trip-service:9081)
└── event/
    └── publisher/   # RabbitMQ publishers (VoteCast)
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_destination` | Primary persistence (db_destination) |
| RabbitMQ | `localhost:5672` | Event publishing |
| Redis | `localhost:6379` | Caching destination rankings |
| trip-service gRPC | `localhost:9081` | IsMember before every write |


### Domain model (db_destination)

**`destination`** — id (UUID), trip_id (UUID), name, description, image_key (MinIO key), estimated_budget
(DECIMAL), currency (VARCHAR), external_url, proposed_by (device UUID), created_at, updated_at.

**`destination_vote`** — id (UUID), destination_id (FK), device_id, rank (nullable — used for ranking vote).
Unique constraint: (destination_id, device_id).

**`destination_comment`** — id (UUID), destination_id (FK), device_id, content (TEXT), created_at.

### gRPC client

Calls `TripGrpcService.IsMember(tripId, deviceId)` on trip-service:9081 before every write operation.

### REST API (`/api/v1/`)

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/trips/{tripId}/destinations` | X-Device-Id + member | Propose a destination |
| GET | `/api/v1/trips/{tripId}/destinations` | X-Device-Id + member | List destinations + vote results |
| POST | `/api/v1/destinations/{id}/vote` | X-Device-Id + member | Vote (upsert) |
| DELETE | `/api/v1/destinations/{id}/vote` | X-Device-Id + member | Retract vote |
| POST | `/api/v1/destinations/{id}/comments` | X-Device-Id + member | Add comment |

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):
- `vote.cast` — routing key `vote.cast` — when a user votes on a destination (consumed by notification-service)
  - Bridged to STOMP by notification-service → clients receive `DESTINATION_VOTE_CAST` frames on `/topic/trips/{tripId}/updates` for real-time vote aggregate updates.

This service does **not** consume any events.

### Security

- Anonymous device-based identity via `DeviceIdFilter` (from `plantogether-common`, auto-configured via `SecurityAutoConfiguration`)
- `X-Device-Id` header extracted and set as SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- No SecurityConfig.java needed — `SecurityAutoConfiguration` handles everything
- Principal name = device UUID string (`authentication.getName()`)
- Public endpoints: `/actuator/health`, `/actuator/info`
- Zero PII stored — only device UUIDs

### Environment variables

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_USER` | `plantogether` |
| `DB_PASSWORD` | `plantogether` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `TRIP_SERVICE_GRPC_HOST` | `localhost` |
| `TRIP_SERVICE_GRPC_PORT` | `9081` |
