# NER Landslide Platform — Database (Supabase)

This project is now the **single database layer** for the NER Landslide
Early-Warning platform. It owns:

- The Postgres/PostGIS schema (`database/schema.sql` + `supabase/migrations/`)
- Row-Level Security policies for direct client access
- Auth (Supabase Auth — signup/login/session, see `src/supabase/auth.js`)
- File storage for field-report photos/videos (`supabase/migrations/20260904_storage.sql`)
- Realtime change feeds for `risk_zones`, `alerts`, `field_reports`, `weather_data`, `risk_predictions`

The `ner-landslide-backend` (Spring Boot) service connects **directly to
this Postgres instance** for all its business logic, ML integration, and
scheduled jobs — it no longer owns any schema of its own.

## Migration order

Apply in this order (via `supabase db push` / `supabase migration up`,
or the SQL editor for a hosted project):

1. `database/schema.sql` — base tables (profiles, risk_zones, landslides,
   rainfall, soil_moisture, infrastructure, field_reports, alerts)
2. `supabase/migrations/20260904_rls_realtime.sql` — RLS policies + realtime
3. `supabase/migrations/20260904_storage.sql` — storage buckets/policies
4. `supabase/migrations/20260905_merge_spring_backend_schema.sql` — folds
   in the fields/tables (`risk_predictions`, `weather_data`, richer
   `risk_zones`, five-tier roles, etc.) that used to be duplicated in the
   Spring Boot backend's own Flyway migrations. **This is the migration
   that reconciles the two backends into one schema** — see its comments
   for exactly what changed and why.

## Roles

Five tiers, matching the Spring backend's `Role` enum (the earlier
`citizen/field_officer/authority/admin` set was a simplification made
before the two backends were reconciled):

`CITIZEN`, `FIELD_OFFICER`, `DISTRICT_ADMIN`, `DISASTER_ADMIN`, `SUPER_ADMIN`

## Local development

```bash
supabase start   # local stack on localhost:54322 (db), 54321 (API)
supabase db push
```

Point the Spring Boot backend's `SUPABASE_DB_URL` at
`jdbc:postgresql://localhost:54322/postgres` (see its `application-dev.yml`).
