-- V1__init.sql
-- Initial schema for the NER Landslide Early-Warning platform.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- users
-- =========================================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL,
    full_name       VARCHAR(150),
    password_hash   TEXT         NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'CITIZEN',
    district        VARCHAR(100),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_users_phone ON users (phone);

-- =========================================================================
-- risk_zones
-- =========================================================================
CREATE TABLE risk_zones (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code                        VARCHAR(40)  NOT NULL UNIQUE,
    name                        VARCHAR(150) NOT NULL,
    state                       VARCHAR(60)  NOT NULL,
    district                    VARCHAR(100) NOT NULL,
    centroid                    geometry(Point,4326)   NOT NULL,
    geometry                    geometry(Polygon,4326),
    current_risk_score          DOUBLE PRECISION,
    current_risk_level          VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    nearby_villages             TEXT,
    affected_roads              TEXT,
    infrastructure_notes        TEXT,
    population_exposure         INTEGER,
    infrastructure_criticality  INTEGER      NOT NULL DEFAULT 1,
    last_updated                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_zones_state_district ON risk_zones (state, district);
CREATE INDEX idx_risk_zones_risk_level ON risk_zones (current_risk_level);
CREATE INDEX idx_risk_zones_centroid ON risk_zones USING GIST (centroid);
CREATE INDEX idx_risk_zones_geometry ON risk_zones USING GIST (geometry);

-- =========================================================================
-- risk_predictions
-- =========================================================================
CREATE TABLE risk_predictions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    risk_zone_id        UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    risk_score          DOUBLE PRECISION NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    risk_level          VARCHAR(20) NOT NULL,
    prediction_window   VARCHAR(40),
    model_version       VARCHAR(40),
    composite_score     DOUBLE PRECISION,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_predictions_zone_created ON risk_predictions (risk_zone_id, created_at);

-- =========================================================================
-- alerts
-- =========================================================================
CREATE TABLE alerts (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    risk_zone_id      UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    severity          VARCHAR(20) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    message           TEXT NOT NULL,
    affected_radius   DOUBLE PRECISION,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ
);

CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_alerts_zone ON alerts (risk_zone_id);

-- =========================================================================
-- field_reports
-- =========================================================================
CREATE TABLE field_reports (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_generated_id    VARCHAR(100) UNIQUE,
    reported_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    risk_zone_id           UUID REFERENCES risk_zones (id) ON DELETE SET NULL,
    report_type            VARCHAR(30) NOT NULL,
    severity                VARCHAR(20) NOT NULL,
    description            TEXT,
    location               geometry(Point,4326) NOT NULL,
    media_url              VARCHAR(500),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ
);

CREATE INDEX idx_reports_status ON field_reports (status);
CREATE INDEX idx_reports_zone ON field_reports (risk_zone_id);
CREATE UNIQUE INDEX idx_reports_client_id ON field_reports (client_generated_id) WHERE client_generated_id IS NOT NULL;
CREATE INDEX idx_reports_location ON field_reports USING GIST (location);

-- =========================================================================
-- weather_data
-- =========================================================================
CREATE TABLE weather_data (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    risk_zone_id            UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    rainfall_mm             DOUBLE PRECISION,
    forecast_rainfall_mm    DOUBLE PRECISION,
    temperature_celsius     DOUBLE PRECISION,
    humidity_percent        DOUBLE PRECISION,
    warning_level           VARCHAR(40),
    source                  VARCHAR(60),
    observed_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_weather_zone_time ON weather_data (risk_zone_id, observed_at);
