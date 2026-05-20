-- ================================================
-- DATABASE MIGRATION: Add Camera Constraints
-- ================================================
-- Date: 2026-05-14
-- Description: Add soft delete, rating, and constraints to cameras table
-- ================================================

-- Add deleted column if not exists
ALTER TABLE cameras 
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;

-- Add rating column if not exists
ALTER TABLE cameras 
ADD COLUMN IF NOT EXISTS rating DECIMAL(3,2) DEFAULT 5.00;

-- Create index for common queries (performance)
CREATE INDEX IF NOT EXISTS idx_cameras_active_deleted 
ON cameras(is_active, deleted);

CREATE INDEX IF NOT EXISTS idx_cameras_rating 
ON cameras(rating DESC);

CREATE INDEX IF NOT EXISTS idx_cameras_created_at 
ON cameras(created_at DESC);

-- Add constraints
ALTER TABLE cameras 
MODIFY COLUMN name VARCHAR(255) NOT NULL;

ALTER TABLE cameras 
MODIFY COLUMN daily_price DECIMAL(12,0) NOT NULL;

-- Ensure images column is not null (default empty array handled in Java)
ALTER TABLE cameras 
MODIFY COLUMN is_active BOOLEAN DEFAULT TRUE;

ALTER TABLE cameras 
MODIFY COLUMN deleted BOOLEAN DEFAULT FALSE;

-- ================================================
-- EXISTING DATA MIGRATION
-- ================================================
-- Set deleted = false for all existing records (assuming they're valid)
UPDATE cameras SET deleted = FALSE WHERE deleted IS NULL;

-- Set default rating for products without rating
UPDATE cameras SET rating = 5.00 WHERE rating IS NULL;

-- ================================================
-- RENTAL ORDER CONSTRAINTS
-- ================================================
-- Create index for order items lookup
CREATE INDEX IF NOT EXISTS idx_rental_order_items_camera 
ON rental_order_items(camera_id);

-- Create index for order status lookup
CREATE INDEX IF NOT EXISTS idx_rental_orders_status 
ON rental_orders(status);
