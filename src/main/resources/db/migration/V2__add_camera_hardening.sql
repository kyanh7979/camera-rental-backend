-- ================================================
-- DATABASE MIGRATION: Camera Hardening
-- ================================================
-- Date: 2026-05-14
-- Description: Add indexes and safety constraints to cameras table
-- ================================================

-- ================================================
-- COLUMNS
-- ================================================

-- Add deleted column if not exists
ALTER TABLE cameras 
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Add rating column with default
ALTER TABLE cameras 
ADD COLUMN IF NOT EXISTS rating DECIMAL(3,2) DEFAULT 5.00;

-- ================================================
-- INDEXES - For Performance
-- ================================================

-- Composite index for common queries
CREATE INDEX IF NOT EXISTS idx_camera_active_deleted 
ON cameras(is_active, deleted);

-- Index for sorting by created date
CREATE INDEX IF NOT EXISTS idx_camera_created 
ON cameras(created_at DESC);

-- Index for rating sorting
CREATE INDEX IF NOT EXISTS idx_camera_rating 
ON cameras(rating DESC);

-- Index for camera_images table
CREATE INDEX IF NOT EXISTS idx_camera_images_camera_id 
ON camera_images(camera_id);

-- ================================================
-- DATA MIGRATION
-- ================================================

-- Set existing records to deleted = false
UPDATE cameras SET deleted = FALSE WHERE deleted IS NULL;

-- Set default rating for products without rating
UPDATE cameras SET rating = 5.00 WHERE rating IS NULL;

-- Ensure is_active is not null
ALTER TABLE cameras MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- ================================================
-- CONSTRAINTS
-- ================================================

-- Ensure name is not null and not empty
ALTER TABLE cameras MODIFY name VARCHAR(255) NOT NULL;

-- Ensure daily_price is positive
ALTER TABLE cameras MODIFY daily_price DECIMAL(12,0) NOT NULL;

-- ================================================
-- VERIFICATION QUERIES
-- ================================================

-- Check total cameras
-- SELECT COUNT(*) AS total_cameras FROM cameras;

-- Check active cameras
-- SELECT COUNT(*) AS active_cameras FROM cameras WHERE is_active = TRUE AND deleted = FALSE;

-- Check cameras with images
-- SELECT COUNT(DISTINCT c.id) FROM cameras c 
-- JOIN camera_images ci ON c.id = ci.camera_id
-- WHERE c.is_active = TRUE AND c.deleted = FALSE;
