-- =====================================================
-- SUPABASE REALTIME
-- =====================================================

ALTER PUBLICATION supabase_realtime
ADD TABLE alerts;

ALTER PUBLICATION supabase_realtime
ADD TABLE risk_zones;

ALTER PUBLICATION supabase_realtime
ADD TABLE field_reports;