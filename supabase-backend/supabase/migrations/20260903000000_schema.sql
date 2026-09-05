CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT,
    phone TEXT,
    role TEXT NOT NULL DEFAULT 'citizen'
        CHECK (role IN (
            'citizen',
            'field_officer',
            'authority',
            'admin'
        )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE risk_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    risk_level TEXT NOT NULL
        CHECK (risk_level IN (
            'LOW',
            'MEDIUM',
            'HIGH',
            'CRITICAL'
        )),
    risk_score NUMERIC(5,2)
        CHECK (risk_score >= 0 AND risk_score <= 100),
    description TEXT,
    geom GEOMETRY(POLYGON, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_zones_geom
ON risk_zones USING GIST (geom);

CREATE TABLE landslides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_zone_id UUID REFERENCES risk_zones(id) ON DELETE SET NULL,
    severity TEXT NOT NULL
        CHECK (severity IN (
            'LOW',
            'MEDIUM',
            'HIGH',
            'CRITICAL'
        )),
    description TEXT,
    occurred_at TIMESTAMPTZ,
    geom GEOMETRY(POINT, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_landslides_geom
ON landslides USING GIST (geom);

CREATE TABLE rainfall (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location GEOMETRY(POINT, 4326) NOT NULL,
    rainfall_mm NUMERIC(8,2) NOT NULL
        CHECK (rainfall_mm >= 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rainfall_location
ON rainfall USING GIST (location);

CREATE TABLE soil_moisture (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location GEOMETRY(POINT, 4326) NOT NULL,
    moisture_percent NUMERIC(5,2) NOT NULL
        CHECK (moisture_percent >= 0 AND moisture_percent <= 100),
    recorded_at TIMESTAMPTZ NOT NULL,
    source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_soil_moisture_location
ON soil_moisture USING GIST (location);

CREATE INDEX idx_soil_moisture_recorded_at
ON soil_moisture(recorded_at);

CREATE TABLE infrastructure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    type TEXT NOT NULL
        CHECK (type IN (
            'ROAD',
            'BRIDGE',
            'BUILDING',
            'HOSPITAL',
            'SCHOOL',
            'OTHER'
        )),
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN (
            'ACTIVE',
            'DAMAGED',
            'BLOCKED',
            'CLOSED'
        )),
    description TEXT,
    geom GEOMETRY(POINT, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_infrastructure_geom
ON infrastructure USING GIST (geom);

CREATE TABLE field_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    report_type TEXT NOT NULL
        CHECK (report_type IN (
            'LANDSLIDE',
            'ROAD_BLOCKAGE',
            'SLOPE_FAILURE',
            'FLOOD',
            'HEAVY_RAINFALL',
            'OTHER'
        )),
    description TEXT,
    photo_url TEXT,
    geom GEOMETRY(POINT, 4326) NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',
            'VERIFIED',
            'REJECTED',
            'RESOLVED'
        )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_field_reports_geom
ON field_reports USING GIST (geom);

CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_zone_id UUID REFERENCES risk_zones(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    severity TEXT NOT NULL
        CHECK (severity IN (
            'LOW',
            'MEDIUM',
            'HIGH',
            'CRITICAL'
        )),
    alert_type TEXT NOT NULL
        CHECK (alert_type IN (
            'LANDSLIDE_RISK',
            'HEAVY_RAINFALL',
            'ROAD_BLOCKAGE',
            'FLOOD_RISK',
            'SLOPE_FAILURE',
            'OTHER'
        )),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_updated_at
BEFORE UPDATE ON profiles
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER risk_zones_updated_at
BEFORE UPDATE ON risk_zones
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER infrastructure_updated_at
BEFORE UPDATE ON infrastructure
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER field_reports_updated_at
BEFORE UPDATE ON field_reports
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();