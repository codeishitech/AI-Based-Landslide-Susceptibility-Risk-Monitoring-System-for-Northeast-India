# NER Landslide Early-Warning Backend

Spring Boot backend for the AI-powered real-time landslide risk monitoring
and early-warning platform for India's North Eastern Region, as specified in
`NER-Landslide-Backend-README.md`.

> **Database and Auth now live in Supabase.** This service used to manage
> its own Postgres schema (Flyway) and issue its own JWTs, duplicating
> work also being done in the separate `supabase-backend` project. That
> duplication has been resolved: **Supabase is now the single database
> handler** (schema, migrations, RLS, storage, auth) for the whole
> platform, and this backend just connects to it. See
> `supabase-backend/README.md` for the schema/migrations and
> `supabase-backend/supabase/migrations/20260905_merge_spring_backend_schema.sql`
> for exactly what was reconciled between the two.

## Stack

- Java 17, Spring Boot 3.3.4
- PostgreSQL 16 + PostGIS — **hosted by Supabase**, not by this service.
  Schema and migrations live in the `supabase-backend` project; this
  service only connects (`SUPABASE_DB_URL`/`SUPABASE_DB_USERNAME`/`SUPABASE_DB_PASSWORD`).
- Spring Data JPA + Hibernate Spatial (no more Flyway here — see above)
- Spring Security + JWT verification — **Supabase Auth issues the tokens**
  (the frontend calls `supabase-js` directly for signup/login); this
  service only verifies the Bearer token (`SUPABASE_JWT_SECRET`) and
  resolves the caller's role from the `profiles` table.
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
  application.yml       Connects to Supabase Postgres (SUPABASE_DB_URL etc.)
  application-dev.yml   Local-Supabase-stack profile (`supabase start`)
```

## Running locally with Docker Compose (recommended)

```bash
# 1. Start Supabase (from the sibling supabase-backend project)
cd ../supabase-backend && supabase start && supabase db push

# 2. Start this backend + Redis + Kafka
cd ../ner-landslide-backend && docker compose up --build
```

This starts Redis, Kafka/Zookeeper, and the backend on `http://localhost:8080`,
connecting to the local Supabase Postgres at `localhost:54322`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

Sample risk zones are seeded by `supabase-backend`'s migrations, not by
this project (see that project's README).

## Running locally without Docker

You'll need a running Supabase project (local `supabase start` or hosted),
plus Redis and Kafka running locally (or point the env vars below at
remote instances).

```bash
export SUPABASE_DB_URL=jdbc:postgresql://localhost:54322/postgres
export SUPABASE_DB_USERNAME=postgres
export SUPABASE_DB_PASSWORD=postgres
export SUPABASE_JWT_SECRET=<from Supabase dashboard: Settings > API > JWT Secret>
export REDIS_HOST=localhost
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

mvn spring-boot:run
```

## Key environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SUPABASE_DB_URL` / `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD` | Supabase Postgres/PostGIS connection | see `application.yml` |
| `SUPABASE_JWT_SECRET` | Verifies Supabase-issued JWTs — **must match your Supabase project's JWT secret** | dev-only placeholder |
| `REDIS_HOST` / `REDIS_PORT` | Cache | `localhost:6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Event bus | `localhost:9092` |
| `ML_SERVICE_URL` | Base URL of the separate Python/FastAPI ML risk engine | `http://localhost:8000` |
| `WEATHER_API_URL` / `WEATHER_API_KEY` | External weather provider | — |
| `SATELLITE_API_URL` | External satellite/EO provider | — |
| `SATELLITE_MOCK_ENABLED` | Use deterministic mock satellite features instead of a real provider | `true` |

## Authentication & RBAC

Registration and login now happen **directly against Supabase Auth from
the frontend** (`supabase-js` `signUp`/`signInWithPassword`, see
`supabase-backend/src/supabase/auth.js`) — this backend no longer issues
or stores credentials. It verifies the resulting Bearer JWT on every
request and resolves the caller's role from the `profiles` table
(`GET /api/v1/auth/me` exposes this to clients that want it from the API).

Roles: `CITIZEN`, `FIELD_OFFICER`, `DISTRICT_ADMIN`, `DISASTER_ADMIN`,
`SUPER_ADMIN`. New signups default to `CITIZEN`; elevated roles must be
set directly in the `profiles` table (or via an internal admin workflow) —
enforced by Supabase RLS, not by this backend. See `SecurityConfig` for the
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
Supabase Postgres + Kafka) are a natural next addition once this needs CI
coverage.

## Notes on scope

- The satellite/EO integration (`SatelliteClient`) ships with a deterministic
  mock feed by default since no real provider was specified — swap
  `SATELLITE_MOCK_ENABLED=false` and set `SATELLITE_API_URL` once a provider
  is chosen; the feature-set contract sent to the ML engine won't need to change.
- SMS/push notification delivery is intentionally out of scope for this
  backend — `ALERT_TRIGGERED`/`ALERT_RESOLVED` Kafka events are the
  integration point for a dedicated notification service.
- Database ownership, auth, and file storage moved to Supabase (see top of
  this README). This backend's own `users` table, Flyway migrations,
  password hashing, and JWT issuance were removed as duplicate work —
  Supabase now owns all of that, and this service only reads/writes the
  same Postgres instance and verifies Supabase's JWTs.
