-- ============================================
-- FIELD REPORT STORAGE
-- ============================================

-- 1. Create bucket for field report photos
INSERT INTO storage.buckets (id, name, public)
VALUES (
  'field-report-photos',
  'field-report-photos',
  false
)
ON CONFLICT (id) DO NOTHING;


-- 2. Create bucket for field report videos
INSERT INTO storage.buckets (id, name, public)
VALUES (
  'field-report-videos',
  'field-report-videos',
  false
)
ON CONFLICT (id) DO NOTHING;


-- ============================================
-- PHOTO BUCKET POLICIES
-- ============================================

-- Authenticated users can upload photos
CREATE POLICY "Authenticated users can upload field report photos"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
  bucket_id = 'field-report-photos'
);


-- Authenticated users can view photos
CREATE POLICY "Authenticated users can view field report photos"
ON storage.objects
FOR SELECT
TO authenticated
USING (
  bucket_id = 'field-report-photos'
);


-- Users can update their own photos
CREATE POLICY "Users can update their own field report photos"
ON storage.objects
FOR UPDATE
TO authenticated
USING (
  bucket_id = 'field-report-photos'
  AND owner_id = (SELECT auth.uid()::text)
)
WITH CHECK (
  bucket_id = 'field-report-photos'
);


-- Users can delete their own photos
CREATE POLICY "Users can delete their own field report photos"
ON storage.objects
FOR DELETE
TO authenticated
USING (
  bucket_id = 'field-report-photos'
  AND owner_id = (SELECT auth.uid()::text)
);


-- ============================================
-- VIDEO BUCKET POLICIES
-- ============================================

-- Authenticated users can upload videos
CREATE POLICY "Authenticated users can upload field report videos"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
  bucket_id = 'field-report-videos'
);


-- Authenticated users can view videos
CREATE POLICY "Authenticated users can view field report videos"
ON storage.objects
FOR SELECT
TO authenticated
USING (
  bucket_id = 'field-report-videos'
);


-- Users can update their own videos
CREATE POLICY "Users can update their own field report videos"
ON storage.objects
FOR UPDATE
TO authenticated
USING (
  bucket_id = 'field-report-videos'
  AND owner_id = (SELECT auth.uid()::text)
)
WITH CHECK (
  bucket_id = 'field-report-videos'
);


-- Users can delete their own videos
CREATE POLICY "Users can delete their own field report videos"
ON storage.objects
FOR DELETE
TO authenticated
USING (
  bucket_id = 'field-report-videos'
  AND owner_id = (SELECT auth.uid()::text)
);