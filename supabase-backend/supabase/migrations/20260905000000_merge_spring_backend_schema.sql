-- ============================================================
-- NER DISASTER PLATFORM
-- MERGE MIGRATION: fold the fields/tables that used to live only
-- in the Spring Boot backend's own Postgres schema into Supabase,
-- which is now the single source of truth for the database.
--
-- After this migration, the Spring Boot service no longer owns
-- any schema (no more Flyway V1__init.sql/V2__seed_data.sql) and
-- connects directly to this Supabase Postgres instance instead.
-- ============================================================

-- ------------------------------------------------------------
-- 1. ROLES: adopt the Spring backend's five-tier role model.
--    ('authority'/'admin' were a simplification made before the
--    two backends were reconciled — replaced with the richer set.)
-- ------------------------------------------------------------

ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_role_check;

ALTER TABLE public.profiles
    ALTER COLUMN role SET DEFAULT 'CITIZEN';

UPDATE public.profiles SET role = 'CITIZEN' WHERE role = 'citizen';
UPDATE public.profiles SET role = 'FIELD_OFFICER' WHERE role = 'field_officer';
UPDATE public.profiles SET role = 'DISTRICT_ADMIN' WHERE role = 'authority';
UPDATE public.profiles SET role = 'SUPER_ADMIN' WHERE role = 'admin';

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_role_check CHECK (role IN (
        'CITIZEN',
        'FIELD_OFFICER',
        'DISTRICT_ADMIN',
        'DISASTER_ADMIN',
        'SUPER_ADMIN'
    ));

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS district TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- get_my_role() already reads profiles.role, so it keeps working
-- unchanged with the new uppercase values.

-- ------------------------------------------------------------
-- 2. RISK ZONES: bring in the extra fields the Spring backend
--    needed (state/district context, exposure, code, etc).
-- ------------------------------------------------------------

ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS code VARCHAR(40) UNIQUE;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS state VARCHAR(60);
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS district VARCHAR(100);
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS nearby_villages TEXT;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS affected_roads TEXT;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS infrastructure_notes TEXT;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS population_exposure INTEGER;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS infrastructure_criticality INTEGER NOT NULL DEFAULT 1;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Rename risk_score/risk_level to the names the Spring entity expects,
-- keeping both readable during transition via generated columns is
-- overkill here — just rename directly since this is the single DB now.
ALTER TABLE public.risk_zones RENAME COLUMN risk_score TO current_risk_score;
ALTER TABLE public.risk_zones RENAME COLUMN risk_level TO current_risk_level;
ALTER TABLE public.risk_zones ALTER COLUMN current_risk_level SET DEFAULT 'LOW';

-- The Spring entity models two separate geometries: a `centroid` point
-- (fast radius/"nearby" queries) and a `geometry` polygon (containment
-- queries). Supabase's schema only had a single polygon `geom` column —
-- rename it to `geometry` and derive `centroid` from it so existing rows
-- keep their boundary and gain a matching centroid automatically.
ALTER TABLE public.risk_zones RENAME COLUMN geom TO geometry;
ALTER TABLE public.risk_zones ALTER COLUMN geometry DROP NOT NULL;
ALTER TABLE public.risk_zones ADD COLUMN IF NOT EXISTS centroid GEOMETRY(POINT, 4326);
UPDATE public.risk_zones SET centroid = ST_Centroid(geometry) WHERE centroid IS NULL AND geometry IS NOT NULL;

DROP INDEX IF EXISTS idx_risk_zones_geom;
CREATE INDEX IF NOT EXISTS idx_risk_zones_geometry ON public.risk_zones USING GIST (geometry);
CREATE INDEX IF NOT EXISTS idx_risk_zones_centroid ON public.risk_zones USING GIST (centroid);
CREATE INDEX IF NOT EXISTS idx_risk_zones_state_district ON public.risk_zones (state, district);
CREATE INDEX IF NOT EXISTS idx_risk_zones_risk_level ON public.risk_zones (current_risk_level);

-- `code` and `centroid` are NOT NULL on the Spring entity. Backfill any
-- existing rows before enforcing that, then lock it in.
UPDATE public.risk_zones SET code = 'ZONE-' || substr(id::text, 1, 8) WHERE code IS NULL;
ALTER TABLE public.risk_zones ALTER COLUMN code SET NOT NULL;
-- centroid is left nullable if a zone has no geometry yet (e.g. drawn
-- later in the admin UI); the Spring service layer should populate it
-- before persisting new zones.

-- ------------------------------------------------------------
-- 3. RISK PREDICTIONS: existed only in the Spring backend's own
--    Postgres DB. Now created here so Supabase is authoritative.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.risk_predictions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_zone_id        UUID NOT NULL REFERENCES public.risk_zones (id) ON DELETE CASCADE,
    risk_score          DOUBLE PRECISION NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    risk_level          TEXT NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    prediction_window   VARCHAR(40),
    model_version       VARCHAR(40),
    composite_score     DOUBLE PRECISION,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_predictions_zone_created ON public.risk_predictions (risk_zone_id, created_at);

ALTER TABLE public.risk_predictions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "risk_predictions_select"
ON public.risk_predictions
FOR SELECT
TO authenticated
USING (true);

-- Only the ML pipeline / trusted backend roles write raw predictions.
CREATE POLICY "risk_predictions_insert"
ON public.risk_predictions
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('DISASTER_ADMIN', 'SUPER_ADMIN')
);

ALTER PUBLICATION supabase_realtime ADD TABLE public.risk_predictions;

-- ------------------------------------------------------------
-- 4. FIELD REPORTS: union of both backends' report types/statuses,
--    plus the client-generated id used for offline sync dedupe.
-- ------------------------------------------------------------

ALTER TABLE public.field_reports DROP CONSTRAINT IF EXISTS field_reports_report_type_check;
ALTER TABLE public.field_reports
    ADD CONSTRAINT field_reports_report_type_check CHECK (report_type IN (
        'LANDSLIDE',
        'ROAD_BLOCKAGE',
        'SOIL_CRACK',
        'SLOPE_MOVEMENT',
        'FLOODING',
        'INFRASTRUCTURE_DAMAGE',
        'OTHER'
    ));

ALTER TABLE public.field_reports DROP CONSTRAINT IF EXISTS field_reports_status_check;
ALTER TABLE public.field_reports
    ADD CONSTRAINT field_reports_status_check CHECK (status IN (
        'PENDING',
        'UNDER_REVIEW',
        'VERIFIED',
        'REJECTED',
        'RESOLVED'
    ));

ALTER TABLE public.field_reports DROP CONSTRAINT IF EXISTS field_reports_severity_check;
ALTER TABLE public.field_reports ADD COLUMN IF NOT EXISTS severity TEXT NOT NULL DEFAULT 'LOW'
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

ALTER TABLE public.field_reports ADD COLUMN IF NOT EXISTS client_generated_id VARCHAR(100) UNIQUE;
ALTER TABLE public.field_reports RENAME COLUMN photo_url TO media_url;

-- Match the Spring entity's column name for the report's point geometry.
ALTER TABLE public.field_reports RENAME COLUMN geom TO location;
DROP INDEX IF EXISTS idx_field_reports_geom;
CREATE INDEX IF NOT EXISTS idx_reports_location ON public.field_reports USING GIST (location);

-- Match the Spring entity's FK column name (nullable — a report can
-- outlive/be detached from the reporting user's profile).
ALTER TABLE public.field_reports RENAME COLUMN user_id TO reported_by;
ALTER TABLE public.field_reports ALTER COLUMN reported_by DROP NOT NULL;
ALTER TABLE public.field_reports
    DROP CONSTRAINT IF EXISTS field_reports_user_id_fkey,
    ADD CONSTRAINT field_reports_reported_by_fkey
        FOREIGN KEY (reported_by) REFERENCES public.profiles (id) ON DELETE SET NULL;

-- ------------------------------------------------------------
-- 5. ALERTS: adopt the Spring backend's lifecycle status model
--    instead of a plain is_active boolean.
-- ------------------------------------------------------------

ALTER TABLE public.alerts ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'EXPIRED'));

UPDATE public.alerts SET status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'RESOLVED' END;

DROP POLICY IF EXISTS "Authenticated users can view active alerts" ON public.alerts;
DROP POLICY IF EXISTS "alerts_select" ON public.alerts;
CREATE POLICY "alerts_select" ON public.alerts FOR SELECT TO authenticated
USING (true);

ALTER TABLE public.alerts DROP COLUMN IF EXISTS is_active;
ALTER TABLE public.alerts ALTER COLUMN alert_type SET DEFAULT 'LANDSLIDE_RISK';
ALTER TABLE public.alerts ALTER COLUMN alert_type DROP NOT NULL;
ALTER TABLE public.alerts ADD COLUMN IF NOT EXISTS affected_radius DOUBLE PRECISION;
ALTER TABLE public.alerts ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_alerts_status ON public.alerts (status);

-- ------------------------------------------------------------
-- 6. WEATHER DATA: the Spring backend reads/writes a single
--    zone-linked weather_data table (rainfall + forecast + temp +
--    humidity together), which didn't exist in the Supabase schema.
--    The existing rainfall/soil_moisture tables stay as-is — they
--    hold finer-grained raw sensor readings that weather_data
--    doesn't replace.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.weather_data (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_zone_id            UUID NOT NULL REFERENCES public.risk_zones (id) ON DELETE CASCADE,
    rainfall_mm             DOUBLE PRECISION,
    forecast_rainfall_mm    DOUBLE PRECISION,
    temperature_celsius     DOUBLE PRECISION,
    humidity_percent        DOUBLE PRECISION,
    warning_level           VARCHAR(40),
    source                  VARCHAR(60),
    observed_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_weather_zone_time ON public.weather_data (risk_zone_id, observed_at);

ALTER TABLE public.weather_data ENABLE ROW LEVEL SECURITY;

CREATE POLICY "weather_data_select"
ON public.weather_data
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "weather_data_insert"
ON public.weather_data
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN')
);

ALTER PUBLICATION supabase_realtime ADD TABLE public.weather_data;

-- ------------------------------------------------------------
-- 7. RE-CREATE RLS POLICIES THAT REFERENCED OLD ROLE NAMES
--    ('authority'/'admin') WITH THE NEW FIVE-TIER NAMES.
-- ------------------------------------------------------------

DROP POLICY IF EXISTS "profiles_select" ON public.profiles;
CREATE POLICY "profiles_select" ON public.profiles FOR SELECT TO authenticated
USING (id = auth.uid() OR public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "profiles_update" ON public.profiles;
CREATE POLICY "profiles_update" ON public.profiles FOR UPDATE TO authenticated
USING (id = auth.uid() OR public.get_my_role() = 'SUPER_ADMIN')
WITH CHECK ((id = auth.uid() AND role = public.get_my_role()) OR public.get_my_role() = 'SUPER_ADMIN');

DROP POLICY IF EXISTS "risk_zones_insert" ON public.risk_zones;
CREATE POLICY "risk_zones_insert" ON public.risk_zones FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "risk_zones_update" ON public.risk_zones;
CREATE POLICY "risk_zones_update" ON public.risk_zones FOR UPDATE TO authenticated
USING (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'))
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "risk_zones_delete" ON public.risk_zones;
CREATE POLICY "risk_zones_delete" ON public.risk_zones FOR DELETE TO authenticated
USING (public.get_my_role() = 'SUPER_ADMIN');

DROP POLICY IF EXISTS "landslides_insert" ON public.landslides;
CREATE POLICY "landslides_insert" ON public.landslides FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "landslides_update" ON public.landslides;
CREATE POLICY "landslides_update" ON public.landslides FOR UPDATE TO authenticated
USING (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'))
WITH CHECK (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "landslides_delete" ON public.landslides;
CREATE POLICY "landslides_delete" ON public.landslides FOR DELETE TO authenticated
USING (public.get_my_role() = 'SUPER_ADMIN');

DROP POLICY IF EXISTS "rainfall_insert" ON public.rainfall;
CREATE POLICY "rainfall_insert" ON public.rainfall FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "soil_moisture_insert" ON public.soil_moisture;
CREATE POLICY "soil_moisture_insert" ON public.soil_moisture FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "infrastructure_insert" ON public.infrastructure;
CREATE POLICY "infrastructure_insert" ON public.infrastructure FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "infrastructure_update" ON public.infrastructure;
CREATE POLICY "infrastructure_update" ON public.infrastructure FOR UPDATE TO authenticated
USING (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'))
WITH CHECK (public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "infrastructure_delete" ON public.infrastructure;
CREATE POLICY "infrastructure_delete" ON public.infrastructure FOR DELETE TO authenticated
USING (public.get_my_role() = 'SUPER_ADMIN');

DROP POLICY IF EXISTS "field_reports_select" ON public.field_reports;
CREATE POLICY "field_reports_select" ON public.field_reports FOR SELECT TO authenticated
USING (reported_by = auth.uid() OR public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "field_reports_insert" ON public.field_reports;
CREATE POLICY "field_reports_insert" ON public.field_reports FOR INSERT TO authenticated
WITH CHECK (reported_by = auth.uid() AND public.get_my_role() IN ('CITIZEN', 'FIELD_OFFICER'));

DROP POLICY IF EXISTS "field_reports_update" ON public.field_reports;
CREATE POLICY "field_reports_update" ON public.field_reports FOR UPDATE TO authenticated
USING (
    (reported_by = auth.uid() AND status = 'PENDING')
    OR public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN')
)
WITH CHECK (
    (reported_by = auth.uid() AND status = 'PENDING')
    OR public.get_my_role() IN ('FIELD_OFFICER', 'DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN')
);

DROP POLICY IF EXISTS "field_reports_delete" ON public.field_reports;
CREATE POLICY "field_reports_delete" ON public.field_reports FOR DELETE TO authenticated
USING (public.get_my_role() = 'SUPER_ADMIN');

DROP POLICY IF EXISTS "alerts_insert" ON public.alerts;
CREATE POLICY "alerts_insert" ON public.alerts FOR INSERT TO authenticated
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "alerts_update" ON public.alerts;
CREATE POLICY "alerts_update" ON public.alerts FOR UPDATE TO authenticated
USING (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'))
WITH CHECK (public.get_my_role() IN ('DISTRICT_ADMIN', 'DISASTER_ADMIN', 'SUPER_ADMIN'));

DROP POLICY IF EXISTS "alerts_delete" ON public.alerts;
CREATE POLICY "alerts_delete" ON public.alerts FOR DELETE TO authenticated
USING (public.get_my_role() = 'SUPER_ADMIN');
