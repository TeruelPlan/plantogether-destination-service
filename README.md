# Destination Service

> Service de gestion des propositions et votes de destinations

## Rôle dans l'architecture

Le Destination Service gère les propositions collectives de destinations de voyage. Les participants proposent des lieux
de vacances avec descriptions, photos et estimations budgétaires. Le service offre trois modes de vote (scrutin simple,
approbation multi-choix, classement) pour que le groupe décide collectivement de la destination finale du voyage.

## Fonctionnalités

- Proposition de destinations avec photo, description et budget
- Trois modes de vote : scrutin simple, approbation, classement
- Stockage des photos dans MinIO
- Gestion de l'historique des propositions
- Calcul automatique de la destination gagnante
- Commentaires sur les propositions
- Vote en temps réel avec mise à jour du classement

## Endpoints REST

| Méthode | Endpoint                                     | Description                          |
|---------|----------------------------------------------|--------------------------------------|
| POST    | `/api/trips/{tripId}/destinations`           | Proposer une destination             |
| GET     | `/api/trips/{tripId}/destinations`           | Lister les destinations d'un voyage  |
| GET     | `/api/destinations/{destinationId}`          | Récupérer une destination            |
| PUT     | `/api/destinations/{destinationId}`          | Modifier une destination (créateur)  |
| DELETE  | `/api/destinations/{destinationId}`          | Supprimer une destination (créateur) |
| POST    | `/api/destinations/{destinationId}/vote`     | Voter pour une destination           |
| PUT     | `/api/destinations/{destinationId}/vote`     | Modifier son vote                    |
| GET     | `/api/destinations/{destinationId}/votes`    | Voir les résultats du vote           |
| POST    | `/api/destinations/{destinationId}/comments` | Ajouter un commentaire               |
| GET     | `/api/destinations/{destinationId}/comments` | Récupérer les commentaires           |

## Modèle de données

**Destination**

- `id` (UUID) : identifiant unique
- `trip_id` (UUID, FK) : voyage associé
- `title` (String) : nom de la destination
- `description` (String, nullable) : description détaillée
- `photo_key` (String, nullable) : clé de l'image sur MinIO
- `budget_estimate` (BigDecimal, nullable) : estimation du budget
- `currency` (String, default: EUR) : devise
- `latitude` (Double, nullable) : coordonnée géographique
- `longitude` (Double, nullable) : coordonnée géographique
- `proposed_by` (UUID) : ID Keycloak du proposant
- `created_at` (Timestamp)
- `updated_at` (Timestamp)

**DestinationVote**

- `id` (UUID)
- `destination_id` (UUID, FK)
- `keycloak_id` (UUID) : votant
- `vote_type` (ENUM: SIMPLE, APPROVAL, RANKING) : type de vote
- `vote_value` (Integer/Boolean) : valeur du vote selon le type
- `voted_at` (Timestamp)

**DestinationComment**

- `id` (UUID)
- `destination_id` (UUID, FK)
- `keycloak_id` (UUID) : auteur du commentaire
- `content` (String) : texte du commentaire
- `created_at` (Timestamp)

## Événements (RabbitMQ)

**Publie :**

- `DestinationProposed` — Émis lors d'une nouvelle proposition
- `DestinationVoted` — Émis lors d'un nouveau vote
- `DestinationWon` — Émis quand une destination remporte le vote

**Consomme :**

- `TripCreated` — Pour initialiser les paramètres de vote du voyage
- `MemberJoined` — Pour préparer les votes des nouveaux membres

## Configuration

```yaml
server:
  port: 8083
  servlet:
    context-path: /
    
spring:
  application:
    name: plantogether-destination-service
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:postgresql://postgres:5432/plantogether_destination
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}

keycloak:
  serverUrl: ${KEYCLOAK_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:plantogether}
  clientId: ${KEYCLOAK_CLIENT_ID}
  
minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
  accessKey: ${MINIO_ACCESS_KEY}
  secretKey: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET:plantogether}
```

## Lancer en local

```bash
# Prérequis : Docker Compose (infra), Java 21+, Maven 3.9+

# Option 1 : Maven
mvn spring-boot:run

# Option 2 : Docker
docker build -t plantogether-destination-service .
docker run -p 8083:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e MINIO_ENDPOINT=http://host.docker.internal:9000 \
  plantogether-destination-service
```

## Dépendances

- **Keycloak 24+** : authentification et autorisation
- **PostgreSQL 16** : persistance des destinations et votes
- **RabbitMQ** : publication d'événements
- **Redis** : cache du classement des destinations
- **MinIO** : stockage des photos de destinations
- **Spring Boot 3.3.6** : framework web
- **Spring Cloud Netflix Eureka** : service discovery

## Modes de vote

### Scrutin simple (SIMPLE)

Une seule destination peut être sélectionnée par participant. La destination avec le plus de votes gagne.

### Approbation (APPROVAL)

Chaque participant peut voter pour plusieurs destinations. La destination avec le plus d'approbations gagne.

### Classement (RANKING)

Chaque participant classe les destinations. Les scores sont calculés avec un système de Borda : position 1 = n points,
position 2 = n-1 points, etc.

## Notes de sécurité

- Les propositions ne peuvent être modifiées que par leur créateur
- Tous les endpoints requièrent authentification Keycloak
- Les photos sont validées (format, taille max 10 MB)
- Zéro PII stockée (seuls les UUIDs Keycloak)
- Les commentaires sont modérables par les organisateurs
