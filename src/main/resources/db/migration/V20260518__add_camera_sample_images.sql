-- ============================================
-- MIGRATION: Add camera_sample_images table
-- Date: 2026-05-18
-- Description: Store sample images (real photos taken with the camera)
-- ============================================

-- Create camera_sample_images table
CREATE TABLE IF NOT EXISTS camera_sample_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    title VARCHAR(255),
    display_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_sample_images_camera 
        FOREIGN KEY (camera_id) 
        REFERENCES cameras(id) 
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    
    INDEX idx_sample_images_camera_id (camera_id),
    INDEX idx_sample_images_display_order (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- ROLLBACK SCRIPT (if needed)
-- ============================================
-- DROP TABLE IF EXISTS camera_sample_images;
