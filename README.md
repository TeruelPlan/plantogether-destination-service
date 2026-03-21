# Destination Service

> Service de gestion des propositions et votes de destinations

## Rôle dans l'architecture

Le Destination Service gère les propositions collectives de destinations de voyage. Les participants proposent des lieux
avec descriptions, photos et estimations budgétaires. Le groupe vote pour choisir la destination finale. Chaque
opération est sécurisée par une vérification d'appartenance au trip via gRPC.

## Fonctionnalités

- Proposition de destinations (nom, description, image, budget estimé, URL externe)
- Vote pour une destination (un vote par utilisateur — rank nullable pour le mode classement)
- Commentaires sur les propositions
- Vérification d'appartenance au trip via gRPC (TripService.CheckMembership)
- Publication d'événements `vote.events` vers RabbitMQ

## Endpoints REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/trips/{tripId}/destinations` | Proposer une destination |
| GET | `/api/v1/trips/{tripId}/destinations` | Liste + résultats de vote |
| POST | `/api/v1/destinations/{id}/vote` | Voter pour une destination |
| DELETE | `/api/v1/destinations/{id}/vote` | Retirer son vote |
| POST | `/api/v1/destinations/{id}/comments` | Commenter une proposition |

## gRPC Client

- `TripService.CheckMembership(tripId, userId)` — vérifie l'appartenance avant toute opération

## Modèle de données (`db_destination`)

**destination**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | Identifiant unique (UUID v7) |
| `trip_id` | UUID NOT NULL | Référence au trip |
| `name` | VARCHAR(255) NOT NULL | Nom de la destination |
| `description` | TEXT NULLABLE | Description libre |
| `image_key` | VARCHAR(500) NULLABLE | Clé MinIO de la photo |
| `estimated_budget` | DECIMAL NULLABLE | Budget estimé |
| `currency` | VARCHAR(3) NULLABLE | Devise (ISO 4217) |
| `external_url` | VARCHAR(512) NULLABLE | Lien externe (guide, réservation) |
| `proposed_by` | UUID NOT NULL | keycloak_id du proposant |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

**destination_vote**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `destination_id` | UUID NOT NULL FK→destination | |
| `keycloak_id` | UUID NOT NULL | Votant |
| `rank` | INT NULLABLE | Rang (pour le mode classement) |

Contrainte unique : `(destination_id, keycloak_id)` — un vote par destination par utilisateur

**destination_comment**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `destination_id` | UUID NOT NULL FK→destination | |
| `keycloak_id` | UUID NOT NULL | Auteur du commentaire |
| `content` | TEXT NOT NULL | Contenu |
| `created_at` | TIMESTAMP NOT NULL | |

## Événements RabbitMQ (Exchange : `plantogether.events`)

**Publie :**

| Routing Key | Déclencheur |
|-------------|-------------|
| `vote.cast` | Vote enregistré ou modifié |

**Consomme :** aucun

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

## Lancer en local

```bash
# Prérequis : docker compose --profile essential up -d
# + plantogether-proto et plantogether-common installés

mvn spring-boot:run
```

## Dépendances

- **Keycloak 24+** : validation JWT
- **PostgreSQL 16** (`db_destination`) : destinations, votes, commentaires
- **RabbitMQ** : publication d'événements (`vote.cast`)
- **Redis** : rate limiting (Bucket4j)
- **Trip Service** (gRPC 9081) : vérification d'appartenance
- **File Service** (gRPC 9088) : génération de presigned URLs pour les images
- **plantogether-proto** : contrats gRPC (client)
- **plantogether-common** : DTOs events, CorsConfig

## Sécurité

- Tous les endpoints requièrent un token Bearer Keycloak valide
- L'appartenance au trip est vérifiée via gRPC à chaque opération
- Zero PII stockée (uniquement des `keycloak_id`)
- La résolution des noms d'utilisateurs se fait côté Flutter (pas de PII côté serveur)
