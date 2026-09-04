-- ============================================================
-- NER DISASTER PLATFORM
-- RLS + REALTIME
-- ============================================================

-- 1. Helper function: current user's role
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role
    FROM public.profiles
    WHERE id = auth.uid()
$$;


-- 2. Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.risk_zones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.landslides ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rainfall ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.soil_moisture ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.infrastructure ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.field_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;


-- 3. PROFILES

CREATE POLICY "profiles_select"
ON public.profiles
FOR SELECT
TO authenticated
USING (
    id = auth.uid()
    OR public.get_my_role() IN ('authority', 'admin')
);

CREATE POLICY "profiles_insert"
ON public.profiles
FOR INSERT
TO authenticated
WITH CHECK (
    id = auth.uid()
);

CREATE POLICY "profiles_update"
ON public.profiles
FOR UPDATE
TO authenticated
USING (
    id = auth.uid()
    OR public.get_my_role() = 'admin'
)
WITH CHECK (
    (
        id = auth.uid()
        AND role = public.get_my_role()
    )
    OR public.get_my_role() = 'admin'
);


-- 4. RISK ZONES

CREATE POLICY "risk_zones_select"
ON public.risk_zones
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "risk_zones_insert"
ON public.risk_zones
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);

CREATE POLICY "risk_zones_update"
ON public.risk_zones
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);

CREATE POLICY "risk_zones_delete"
ON public.risk_zones
FOR DELETE
TO authenticated
USING (
    public.get_my_role() = 'admin'
);


-- 5. LANDSLIDES

CREATE POLICY "landslides_select"
ON public.landslides
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "landslides_insert"
ON public.landslides
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "landslides_update"
ON public.landslides
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
)
WITH CHECK (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "landslides_delete"
ON public.landslides
FOR DELETE
TO authenticated
USING (
    public.get_my_role() = 'admin'
);


-- 6. RAINFALL

CREATE POLICY "rainfall_select"
ON public.rainfall
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "rainfall_insert"
ON public.rainfall
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- 7. SOIL MOISTURE

CREATE POLICY "soil_moisture_select"
ON public.soil_moisture
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "soil_moisture_insert"
ON public.soil_moisture
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- 8. INFRASTRUCTURE

CREATE POLICY "infrastructure_select"
ON public.infrastructure
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "infrastructure_insert"
ON public.infrastructure
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "infrastructure_update"
ON public.infrastructure
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
)
WITH CHECK (
    public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "infrastructure_delete"
ON public.infrastructure
FOR DELETE
TO authenticated
USING (
    public.get_my_role() = 'admin'
);


-- 9. FIELD REPORTS

CREATE POLICY "field_reports_select"
ON public.field_reports
FOR SELECT
TO authenticated
USING (
    user_id = auth.uid()
    OR public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "field_reports_insert"
ON public.field_reports
FOR INSERT
TO authenticated
WITH CHECK (
    user_id = auth.uid()
    AND public.get_my_role() IN (
        'citizen',
        'field_officer'
    )
);

CREATE POLICY "field_reports_update"
ON public.field_reports
FOR UPDATE
TO authenticated
USING (
    (
        user_id = auth.uid()
        AND status = 'PENDING'
    )
    OR public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
)
WITH CHECK (
    (
        user_id = auth.uid()
        AND status = 'PENDING'
    )
    OR public.get_my_role() IN (
        'field_officer',
        'authority',
        'admin'
    )
);

CREATE POLICY "field_reports_delete"
ON public.field_reports
FOR DELETE
TO authenticated
USING (
    public.get_my_role() = 'admin'
);


-- 10. ALERTS

CREATE POLICY "alerts_select"
ON public.alerts
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "alerts_insert"
ON public.alerts
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);

CREATE POLICY "alerts_update"
ON public.alerts
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);

CREATE POLICY "alerts_delete"
ON public.alerts
FOR DELETE
TO authenticated
USING (
    public.get_my_role() = 'admin'
);


-- 11. REALTIME

ALTER PUBLICATION supabase_realtime
ADD TABLE public.alerts;

ALTER PUBLICATION supabase_realtime
ADD TABLE public.risk_zones;

ALTER PUBLICATION supabase_realtime
ADD TABLE public.field_reports;