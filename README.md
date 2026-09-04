# NER Landslide Early-Warning Backend

Spring Boot backend for the AI-powered real-time landslide risk monitoring
and early-warning platform for India's North Eastern Region, as specified in
`NER-Landslide-Backend-README.md`.

## Stack

- Java 17, Spring Boot 3.3.4
- PostgreSQL 16 + PostGIS (geospatial risk zones, field-report locations)
- Spring Data JPA + Hibernate Spatial, Flyway migrations
- Spring Security + JWT (stateless, role-based access control)
- Redis (caching zone snapshots / analytics)
- Kafka (event pipeline: WEATHER_UPDATED, PREDICTION_GENERATED, RISK_LEVEL_CHANGED,
  FIELD_REPORT_CREATED, ALERT_TRIGGERED, ALERT_RESOLVED)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)
- WebClient-based integration clients for the ML risk engine, weather API, and
  satellite feed (satellite defaults to a mock provider — see below)

## Project layout

```
src/main/java/com/ner/landslide/
  config/        Security, Redis, Kafka, OpenAPI, WebClient, Async config
  controller/    REST endpoints
  service/       Business logic (risk evaluation, alerting, reports, auth, analytics)
  repository/    Spring Data JPA repositories, incl. native PostGIS spatial queries
  entity/        JPA entities (+ entity/enums)
  dto/           Request/response payloads
  integration/   Outbound clients: MlRiskClient, WeatherClient, SatelliteClient
  scheduler/     @Scheduled jobs: weather ingestion, prediction refresh, alert expiry
  security/      JWT issuing/validation, filter, UserDetailsService
  exception/     Custom exceptions + GlobalExceptionHandler
  util/          GeoUtils (JTS point construction, Haversine distance)
src/main/resources/
  application.yml
  db/migration/  Flyway SQL (schema + sample NER risk zones)
```

## Running locally with Docker Compose (recommended)

```bash
docker compose up --build
```

This starts PostGIS, Redis, Kafka/Zookeeper, and the backend on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

The Flyway migrations seed six sample risk zones across Meghalaya, Sikkim,
Nagaland, Mizoram, and Arunachal Pradesh so you can exercise the API
immediately (see `GET /api/v1/risk-zones`).

## Running locally without Docker

You'll need PostgreSQL 16+ with the PostGIS extension available, plus Redis
and Kafka running locally (or point the env vars below at remote instances).

```bash
export DB_URL=jdbc:postgresql://localhost:5432/landslide_db
export DB_USERNAME=landslide_user
export DB_PASSWORD=landslide_pass
export REDIS_HOST=localhost
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export JWT_SECRET=$(openssl rand -hex 32)

mvn spring-boot:run
```

## Key environment variables

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostGIS connection | see `application.yml` |
| `REDIS_HOST` / `REDIS_PORT` | Cache | `localhost:6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Event bus | `localhost:9092` |
| `JWT_SECRET` | HMAC signing key — **override in production** | dev-only placeholder |
| `ML_SERVICE_URL` | Base URL of the separate Python/FastAPI ML risk engine | `http://localhost:8000` |
| `WEATHER_API_URL` / `WEATHER_API_KEY` | External weather provider | — |
| `SATELLITE_API_URL` | External satellite/EO provider | — |
| `SATELLITE_MOCK_ENABLED` | Use deterministic mock satellite features instead of a real provider | `true` |

## Authentication & RBAC

Register/login via `POST /api/v1/auth/register` and `/login` to receive a
Bearer JWT. Roles: `CITIZEN`, `FIELD_OFFICER`, `DISTRICT_ADMIN`,
`DISASTER_ADMIN`, `SUPER_ADMIN`. Public self-registration only ever grants
`CITIZEN` or `FIELD_OFFICER`; elevated roles must be provisioned directly in
the database or via an internal admin workflow. See `SecurityConfig` for the
full per-endpoint access matrix.

## ML Pipeline Integration (SIH26001)

The Spring Boot backend acts as the core API gateway and persistence layer, communicating with the Python/FastAPI ML risk microservice (`uvicorn src.api.app:app --host 0.0.0.0 --port 8000`).

- `POST /api/v1/predictions/predict` — proxies `{"latitude":.., "longitude":.., "date":".."}` to `POST /predict` on the ML microservice and returns `{risk_probability, risk_level, top_factors, rainfall_data_used}`.
- `GET /api/v1/predictions/health` — proxies `GET /health` to monitor model loading status and NASA GPM IMERG rainfall coverage.
- `GET /api/v1/predictions/coverage` — proxies `GET /coverage` to retrieve SRTM DEM tile bounding boxes so frontend maps can grey out un-covered regions.

## How a prediction becomes an alert

1. The ML risk engine (or `PredictionRefreshJob`, on a schedule using zone centroid coordinates) posts a raw prediction to `POST /api/v1/predictions`.
2. `PredictionService` validates the payload and calls `RiskEvaluationService`,
   which blends the ML score (confidence-dampened) with recent rainfall,
   corroborating field reports, and population/infrastructure exposure into a
   single **composite score**.
3. The zone's `currentRiskLevel` is updated from that composite score.
4. `AlertService` creates a new `Alert` for HIGH/CRITICAL levels (deduplicated
   within a 6-hour window per zone) and auto-resolves active alerts when a
   zone drops back to MEDIUM/LOW.
5. `RISK_LEVEL_CHANGED` / `ALERT_TRIGGERED` / `ALERT_RESOLVED` events are
   published to Kafka for downstream notification channels.

## Tests

```bash
mvn test
```

Includes unit tests for the `RiskLevel` threshold logic and `GeoUtils`
geometry/distance helpers. Full integration tests (Testcontainers-backed
PostGIS + Kafka) are a natural next addition once this needs CI coverage.

## Notes on scope

- The satellite/EO integration (`SatelliteClient`) ships with a deterministic
  mock feed by default since no real provider was specified — swap
  `SATELLITE_MOCK_ENABLED=false` and set `SATELLITE_API_URL` once a provider
  is chosen; the feature-set contract sent to the ML engine won't need to change.
- SMS/push notification delivery is intentionally out of scope for this
  backend — `ALERT_TRIGGERED`/`ALERT_RESOLVED` Kafka events are the
  integration point for a dedicated notification service.
