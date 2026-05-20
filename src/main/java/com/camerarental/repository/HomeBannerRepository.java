package com.camerarental.repository;

import com.camerarental.entity.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

    /**
     * Get all active banners ordered by display order
     */
    @Query("SELECT b FROM HomeBanner b WHERE b.active = true ORDER BY b.displayOrder ASC, b.updatedAt DESC")
    List<HomeBanner> findAllActive();

    /**
     * Get the first active banner (for homepage)
     */
    @Query("SELECT b FROM HomeBanner b WHERE b.active = true ORDER BY b.displayOrder ASC, b.updatedAt DESC LIMIT 1")
    Optional<HomeBanner> findFirstActive();

    /**
     * Get all banners ordered by display order
     */
    List<HomeBanner> findAllByOrderByDisplayOrderAsc();

    /**
     * Check if an image URL is already in use
     */
    boolean existsByImageUrl(String imageUrl);
}
