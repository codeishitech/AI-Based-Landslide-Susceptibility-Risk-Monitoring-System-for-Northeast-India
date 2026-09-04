-- =====================================================
-- RLS + ROLE BASED ACCESS
-- =====================================================

-- PROFILES

CREATE POLICY "Users can view own profile"
ON profiles
FOR SELECT
TO authenticated
USING (auth.uid() = id);


CREATE POLICY "Users can update own profile"
ON profiles
FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (
    auth.uid() = id
    AND role = public.get_my_role()
);


-- RISK ZONES

CREATE POLICY "Authenticated users can view risk zones"
ON risk_zones
FOR SELECT
TO authenticated
USING (true);


CREATE POLICY "Authority can insert risk zones"
ON risk_zones
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


CREATE POLICY "Authority can update risk zones"
ON risk_zones
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


CREATE POLICY "Authority can delete risk zones"
ON risk_zones
FOR DELETE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
);


-- LANDSLIDES

CREATE POLICY "Authenticated users can view landslides"
ON landslides
FOR SELECT
TO authenticated
USING (true);


CREATE POLICY "Authority can manage landslides"
ON landslides
FOR ALL
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- RAINFALL

CREATE POLICY "Authenticated users can view rainfall"
ON rainfall
FOR SELECT
TO authenticated
USING (true);


CREATE POLICY "Authority can manage rainfall"
ON rainfall
FOR ALL
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- SOIL MOISTURE

CREATE POLICY "Authenticated users can view soil moisture"
ON soil_moisture
FOR SELECT
TO authenticated
USING (true);


CREATE POLICY "Authority can manage soil moisture"
ON soil_moisture
FOR ALL
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- INFRASTRUCTURE

CREATE POLICY "Authenticated users can view infrastructure"
ON infrastructure
FOR SELECT
TO authenticated
USING (true);


CREATE POLICY "Authority can manage infrastructure"
ON infrastructure
FOR ALL
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


-- FIELD REPORTS

CREATE POLICY "Users can view own field reports"
ON field_reports
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);


CREATE POLICY "Users can create own field reports"
ON field_reports
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);


CREATE POLICY "Users can update own pending reports"
ON field_reports
FOR UPDATE
TO authenticated
USING (
    auth.uid() = user_id
    AND status = 'PENDING'
)
WITH CHECK (
    auth.uid() = user_id
);


CREATE POLICY "Field officers can view field reports"
ON field_reports
FOR SELECT
TO authenticated
USING (
    public.get_my_role() IN ('field_officer', 'authority', 'admin')
);


CREATE POLICY "Field officers can update field reports"
ON field_reports
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN ('field_officer', 'authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('field_officer', 'authority', 'admin')
);


-- ALERTS

CREATE POLICY "Authenticated users can view active alerts"
ON alerts
FOR SELECT
TO authenticated
USING (is_active = true);


CREATE POLICY "Authority can create alerts"
ON alerts
FOR INSERT
TO authenticated
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


CREATE POLICY "Authority can update alerts"
ON alerts
FOR UPDATE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
)
WITH CHECK (
    public.get_my_role() IN ('authority', 'admin')
);


CREATE POLICY "Authority can delete alerts"
ON alerts
FOR DELETE
TO authenticated
USING (
    public.get_my_role() IN ('authority', 'admin')
);