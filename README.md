# Destination Service

> Destination proposal and voting management service

## Role in the Architecture

The Destination Service manages collective destination proposals for trips. Participants propose places
with descriptions, photos, and budget estimates. The group votes to choose the final destination. Each
operation is secured by a trip membership check via gRPC.

## Features

- Destination proposals (name, description, image, estimated budget, external URL)
- Voting for a destination (one vote per user — nullable rank for ranking mode)
- Comments on proposals
- Trip membership verification via gRPC (TripService.IsMember)
- Publishing `vote.cast` events to RabbitMQ

## REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/trips/{tripId}/destinations` | Propose a destination |
| GET | `/api/v1/trips/{tripId}/destinations` | List + vote results |
| POST | `/api/v1/destinations/{id}/vote` | Vote for a destination |
| DELETE | `/api/v1/destinations/{id}/vote` | Retract vote |
| POST | `/api/v1/destinations/{id}/comments` | Comment on a proposal |

## gRPC Client

- `TripService.IsMember(tripId, deviceId)` — verifies membership before every operation

## Data Model (`db_destination`)

**destination**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | Unique identifier (UUID v7) |
| `trip_id` | UUID NOT NULL | Trip reference |
| `name` | VARCHAR(255) NOT NULL | Destination name |
| `description` | TEXT NULLABLE | Free-form description |
| `image_key` | VARCHAR(500) NULLABLE | MinIO key for photo |
| `estimated_budget` | DECIMAL NULLABLE | Estimated budget |
| `currency` | VARCHAR(3) NULLABLE | Currency (ISO 4217) |
| `external_url` | VARCHAR(512) NULLABLE | External link (guide, booking) |
| `proposed_by` | UUID NOT NULL | device_id of the proposer |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

**destination_vote**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `destination_id` | UUID NOT NULL FK→destination | |
| `device_id` | UUID NOT NULL | Voter device UUID |
| `rank` | INT NULLABLE | Rank (for ranking mode) |

Unique constraint: `(destination_id, device_id)` — one vote per destination per user

**destination_comment**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `destination_id` | UUID NOT NULL FK→destination | |
| `device_id` | UUID NOT NULL | Comment author device UUID |
| `content` | TEXT NOT NULL | Content |
| `created_at` | TIMESTAMP NOT NULL | |

## RabbitMQ Events (Exchange: `plantogether.events`)

**Publishes:**

| Routing Key | Trigger |
|-------------|---------|
| `vote.cast` | Vote recorded or modified |

**Consumes:** none

## Configuration

```yaml
server:
  port: 8083

spring:
  application:
    name: plantogether-destination-service
  datasource:
    url: jdbc:postgresql://postgres:5432/db_destination
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

grpc:
  client:
    trip-service:
      address: static://trip-service:9081
  server:
    port: 9083
```

## Running Locally

```bash
# Prerequisites: docker compose up -d
# + plantogether-proto and plantogether-common installed

mvn spring-boot:run
```

## Dependencies

- **PostgreSQL 16** (`db_destination`): destinations, votes, comments
- **RabbitMQ**: event publishing (`vote.cast`)
- **Redis**: rate limiting (Bucket4j)
- **Trip Service** (gRPC 9081): membership verification
- **File Service** (gRPC 9088): presigned URL generation for images
- **plantogether-proto**: gRPC contracts (client)
- **plantogether-common**: event DTOs, DeviceIdFilter, SecurityAutoConfiguration, CorsConfig

## Security

- Anonymous device-based identity: `X-Device-Id` header on every request
- `DeviceIdFilter` (from plantogether-common, auto-configured via `SecurityAutoConfiguration`) extracts the device UUID and sets the SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- Trip membership is verified via gRPC at each operation
- Zero PII stored (only `device_id` references)
- User name resolution is performed client-side by Flutter (no PII on the server)
