-- V1__init.sql (H2 database compatibility migration)

CREATE ALIAS IF NOT EXISTS ST_MAKEPOINT FOR "com.ner.landslide.util.H2SpatialFunctions.makePoint";
CREATE ALIAS IF NOT EXISTS ST_SETSRID FOR "com.ner.landslide.util.H2SpatialFunctions.setSRID";
CREATE ALIAS IF NOT EXISTS ST_DWITHIN FOR "com.ner.landslide.util.H2SpatialFunctions.dWithin";
CREATE ALIAS IF NOT EXISTS ST_DISTANCE FOR "com.ner.landslide.util.H2SpatialFunctions.distance";
CREATE ALIAS IF NOT EXISTS ST_CONTAINS FOR "com.ner.landslide.util.H2SpatialFunctions.contains";

CREATE TABLE users (
    id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(150),
    password_hash   VARCHAR(500) NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'CITIZEN',
    district        VARCHAR(100),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE risk_zones (
    id                          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    code                        VARCHAR(40)  NOT NULL UNIQUE,
    name                        VARCHAR(150) NOT NULL,
    state                       VARCHAR(60)  NOT NULL,
    district                    VARCHAR(100) NOT NULL,
    centroid                    GEOMETRY     NOT NULL,
    geometry                    GEOMETRY,
    current_risk_score          DOUBLE PRECISION,
    current_risk_level          VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    nearby_villages             VARCHAR(10000),
    affected_roads              VARCHAR(10000),
    infrastructure_notes        VARCHAR(10000),
    population_exposure         INTEGER,
    infrastructure_criticality  INTEGER      NOT NULL DEFAULT 1,
    last_updated                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_risk_zones_state_district ON risk_zones (state, district);
CREATE INDEX idx_risk_zones_risk_level ON risk_zones (current_risk_level);

CREATE TABLE risk_predictions (
    id                  UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    risk_zone_id        UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    risk_score          DOUBLE PRECISION NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    risk_level          VARCHAR(20) NOT NULL,
    prediction_window   VARCHAR(40),
    model_version       VARCHAR(40),
    composite_score     DOUBLE PRECISION,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_predictions_zone_created ON risk_predictions (risk_zone_id, created_at);

CREATE TABLE alerts (
    id                UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    risk_zone_id      UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    severity          VARCHAR(20) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    message           VARCHAR(10000) NOT NULL,
    affected_radius   DOUBLE PRECISION,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMP WITH TIME ZONE,
    resolved_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_alerts_zone ON alerts (risk_zone_id);

CREATE TABLE field_reports (
    id                     UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    client_generated_id    VARCHAR(100) UNIQUE,
    reported_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    risk_zone_id           UUID REFERENCES risk_zones (id) ON DELETE SET NULL,
    report_type            VARCHAR(30) NOT NULL,
    severity               VARCHAR(20) NOT NULL,
    description            VARCHAR(10000),
    location               GEOMETRY NOT NULL,
    media_url              VARCHAR(500),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reports_status ON field_reports (status);
CREATE INDEX idx_reports_zone ON field_reports (risk_zone_id);

CREATE TABLE weather_data (
    id                      UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    risk_zone_id            UUID NOT NULL REFERENCES risk_zones (id) ON DELETE CASCADE,
    rainfall_mm             DOUBLE PRECISION,
    forecast_rainfall_mm    DOUBLE PRECISION,
    temperature_celsius     DOUBLE PRECISION,
    humidity_percent        DOUBLE PRECISION,
    warning_level           VARCHAR(40),
    source                  VARCHAR(60),
    observed_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_weather_zone_time ON weather_data (risk_zone_id, observed_at);
