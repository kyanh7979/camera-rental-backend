-- ================================================
-- MIGRATION: Camera Images - Real Entity Table
-- ================================================
-- Date: 2026-05-14
-- Description: Create proper camera_images table with entity relationship
-- ================================================

-- Create camera_images table (if not exists)
CREATE TABLE IF NOT EXISTS camera_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_camera_images_camera FOREIGN KEY (camera_id) REFERENCES cameras(id) ON DELETE CASCADE
);

-- Create index for camera_id
CREATE INDEX IF NOT EXISTS idx_camera_images_camera_id ON camera_images(camera_id);

-- ================================================
-- MIGRATE DATA FROM LEGACY TABLE
-- ================================================

-- Note: This assumes camera_images table exists with simple structure
-- If your legacy data is in a different table, adjust accordingly

-- Migrate existing data from old camera_images table to new structure
-- (if legacy table has data, we'll handle it in service layer)

-- ================================================
-- VERIFICATION QUERIES
-- ================================================

-- Check total images
-- SELECT COUNT(*) AS total_images FROM camera_images;

-- Check images per camera
-- SELECT c.name, COUNT(ci.id) AS image_count 
-- FROM cameras c 
-- LEFT JOIN camera_images ci ON c.id = ci.camera_id 
-- GROUP BY c.id;

-- Check for cameras without images
-- SELECT c.id, c.name FROM cameras c 
-- WHERE c.is_active = TRUE AND c.deleted = FALSE 
-- AND c.id NOT IN (SELECT DISTINCT camera_id FROM camera_images);
