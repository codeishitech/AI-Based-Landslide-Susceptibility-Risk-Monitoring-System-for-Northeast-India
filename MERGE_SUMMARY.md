# Merge summary: two backends → one

## What changed

**`supabase-backend/`** is now the single database handler for the
platform: schema, migrations, RLS, auth, and file storage all live here.
New migration `supabase/migrations/20260905_merge_spring_backend_schema.sql`
folds in everything the Spring Boot backend used to define on its own
(five-tier roles, `risk_predictions`, `weather_data`, the extra
`risk_zones` fields, reconciled `field_reports`/`alerts` shapes).

**`ner-landslide-backend/`** (Spring Boot) had its duplicate schema/auth
work removed:

| Removed | Replaced by |
|---|---|
| Flyway migrations (`db/migration/**`, Postgres + H2) | Supabase migrations own the schema |
| `User` entity, `UserRepository` | `Profile` entity/`ProfileRepository`, mapped to Supabase's `profiles` table |
| `CustomUserDetailsService`, token-issuing `JwtService` | `SupabaseJwtService` (verification only) |
| `AuthService`, `POST /auth/register`, `POST /auth/login`, their DTOs | Frontend calls Supabase Auth directly; `GET /api/v1/auth/me` is all that's left |
| Local Postgres in `docker-compose.yml` | Connects to Supabase Postgres (`SUPABASE_DB_URL`) |
| `flyway-core`, `flyway-database-postgresql`, `h2` (pom.xml) | No longer needed |
| `H2SpatialFunctions` util | Dead code once the H2 profile was removed |

Everything else — controllers, services, scheduler jobs, ML/weather/
satellite integration clients, Kafka event publishing, Redis caching —
is untouched; it never duplicated anything in the Supabase project.

## To deploy

1. Apply `supabase-backend`'s migrations to your Supabase project
   (`supabase db push`, in the order listed in its README).
2. Set `SUPABASE_DB_URL` / `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD`
   / `SUPABASE_JWT_SECRET` on the Spring Boot backend (from your Supabase
   project's Settings → Database and Settings → API).
3. Point your frontend's Supabase client (`src/supabase/*`) at the same
   project, and have it call the Spring Boot backend's API for
   everything beyond plain CRUD (predictions, alert generation,
   analytics, scheduled jobs).

## Still worth doing next
- A frontend doesn't exist yet in either project — `src/supabase/` is
  just the auth helper functions, not a UI.
- No integration tests exercise the new Supabase connection end-to-end;
  worth adding once CI is set up (see each README's Tests section).
- `WeatherClient`/`SatelliteClient` external providers are still unset —
  unrelated to this merge, but worth flagging.
