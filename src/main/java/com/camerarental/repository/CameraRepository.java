package com.camerarental.repository;

import com.camerarental.entity.Camera;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {

    Optional<Camera> findBySlug(String slug);

    /**
     * Find by slug excluding deleted cameras
     */
    @Query("SELECT c FROM Camera c WHERE c.slug = :slug AND c.deleted = false")
    Optional<Camera> findBySlugAndDeletedFalse(@Param("slug") String slug);

    /**
     * Find by ID excluding deleted cameras
     */
    @Query("SELECT c FROM Camera c WHERE c.id = :id AND c.deleted = false")
    Optional<Camera> findByIdAndDeletedFalse(@Param("id") Long id);

    /**
     * Check if name exists (excluding deleted)
     */
    @Query("SELECT COUNT(c) > 0 FROM Camera c WHERE LOWER(c.name) = LOWER(:name) AND c.deleted = false")
    boolean existsByNameAndDeletedFalse(@Param("name") String name);

    boolean existsByName(String name);

    // ================================================
    // SEARCH QUERIES - WITH DELETED CHECK
    // ================================================
    
    @Query(
            value = "SELECT DISTINCT c FROM Camera c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.images " +
                    "WHERE c.isActive = true AND c.deleted = false " +
                    "AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(c.brand) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:brand IS NULL OR :brand = '' OR c.brand = :brand) " +
                    "AND (:categoryId IS NULL OR c.category.id = :categoryId) " +
                    "AND (:minPrice IS NULL OR c.dailyPrice >= :minPrice) " +
                    "AND (:maxPrice IS NULL OR c.dailyPrice <= :maxPrice) " +
                    "AND (:available IS NULL OR c.available > 0)",
            countQuery = "SELECT COUNT(DISTINCT c) FROM Camera c WHERE c.isActive = true AND c.deleted = false " +
                    "AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "OR LOWER(c.brand) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:brand IS NULL OR :brand = '' OR c.brand = :brand) " +
                    "AND (:categoryId IS NULL OR c.category.id = :categoryId) " +
                    "AND (:minPrice IS NULL OR c.dailyPrice >= :minPrice) " +
                    "AND (:maxPrice IS NULL OR c.dailyPrice <= :maxPrice) " +
                    "AND (:available IS NULL OR c.available > 0)"
    )
    Page<Camera> searchCameras(
            @Param("keyword") String keyword,
            @Param("brand") String brand,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("available") Boolean available,
            Pageable pageable);

    @Query("SELECT DISTINCT c.brand FROM Camera c WHERE c.brand IS NOT NULL AND c.brand != '' " +
           "AND c.isActive = true AND c.deleted = false ORDER BY c.brand")
    List<String> findDistinctBrands();

    // ================================================
    // SLIDER QUERIES - FETCH IMAGES FOR PERFORMANCE
    // ================================================

    /**
     * Get ALL active cameras for slider with EAGER fetch of images only
     * NOTE: sampleImages loaded separately in service layer to avoid MultipleBagFetchException
     */
    @Query(value = """
            SELECT DISTINCT c FROM Camera c
            LEFT JOIN FETCH c.category
            LEFT JOIN FETCH c.images
            WHERE c.isActive = true AND c.deleted = false
            ORDER BY COALESCE(c.rating, 5.0) DESC, c.createdAt DESC
            """)
    List<Camera> findAllActiveCamerasWithImages();

    /**
     * Get all active cameras (simple query)
     */
    @Query("SELECT c FROM Camera c LEFT JOIN FETCH c.category " +
           "WHERE c.isActive = true AND c.deleted = false " +
           "ORDER BY COALESCE(c.rating, 5.0) DESC, c.createdAt DESC")
    List<Camera> findAllActiveCameras();

    /**
     * Find by ID with images loaded (NO sampleImages to avoid MultipleBagFetchException)
     * sampleImages must be loaded separately via CameraSampleImageRepository
     */
    @Query("SELECT c FROM Camera c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.images " +
           "WHERE c.id = :id AND c.deleted = false")
    Optional<Camera> findByIdWithImages(@Param("id") Long id);

    /**
     * Get ALL cameras for admin with images loaded (NO sampleImages)
     * sampleImages must be loaded separately via CameraSampleImageRepository
     */
    @Query("SELECT c FROM Camera c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.images " +
           "WHERE c.deleted = false ORDER BY c.createdAt DESC")
    List<Camera> findAllByDeletedFalse();

    // ================================================
    // COUNT QUERIES
    // ================================================

    @Query("SELECT COUNT(c) FROM Camera c WHERE c.isActive = true AND c.deleted = false")
    long countActiveCameras();

    // ================================================
    // LOW STOCK QUERIES FOR TELEGRAM BOT
    // ================================================

    @Query("SELECT c FROM Camera c WHERE c.isActive = true AND c.deleted = false AND c.available <= :threshold ORDER BY c.available ASC")
    List<Camera> findByAvailableLessThanEqual(@Param("threshold") Integer threshold);
}
