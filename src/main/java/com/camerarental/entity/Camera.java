package com.camerarental.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cameras", indexes = {
    @Index(name = "idx_camera_active_deleted", columnList = "is_active, deleted"),
    @Index(name = "idx_camera_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String brand;

    private String model;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "daily_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal dailyPrice;

    @Column(precision = 12, scale = 0)
    private BigDecimal deposit;

    @Builder.Default
    private Integer stock = 1;

    @Builder.Default
    private Integer available = 1;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.valueOf(5.00);

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =========================================
    // IMAGE RELATIONSHIP - REAL (ONE-TO-MANY)
    // Maps to camera_images table
    // =========================================
    @JsonIgnore
    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CameraImage> images = new ArrayList<>();

    // =========================================
    // SAMPLE IMAGES RELATIONSHIP - (ONE-TO-MANY)
    // Maps to camera_sample_images table
    // =========================================
    @JsonIgnore
    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CameraSampleImage> sampleImages = new ArrayList<>();

    // =========================================
    // HELPER METHODS FOR IMAGES
    // =========================================

    /**
     * Get all image URLs from the images relationship
     * Returns List<String> for easy JSON serialization
     */
    @JsonIgnore
    public List<String> getImageUrls() {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        return images.stream()
                .map(CameraImage::getImageUrl)
                .filter(url -> url != null && !url.trim().isEmpty())
                .toList();
    }

    /**
     * Add image URL via entity relationship
     */
    public void addImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return;
        
        CameraImage cameraImage = CameraImage.builder()
                .imageUrl(imageUrl)
                .displayOrder(images.size())
                .camera(this)
                .build();
        images.add(cameraImage);
    }

    /**
     * Remove image URL
     */
    public void removeImage(String imageUrl) {
        if (imageUrl == null) return;
        images.removeIf(ci -> imageUrl.equals(ci.getImageUrl()));
    }

    /**
     * Clear all images
     */
    public void clearImages() {
        images.clear();
    }

    /**
     * Set images from list of URLs
     */
    public void setImageUrls(List<String> urls) {
        clearImages();
        if (urls != null) {
            urls.forEach(this::addImage);
        }
    }

    /**
     * Custom setter for images field to accept List<String> from JSON
     * Maps incoming image URLs to CameraImage entities
     */
    @JsonProperty("images")
    public void setImagesFromUrls(List<String> imageUrls) {
        clearImages();
        if (imageUrls != null) {
            imageUrls.forEach(this::addImage);
        }
    }

    /**
     * Custom getter for images field to return List<String> in JSON
     */
    @JsonProperty("images")
    public List<String> getImagesAsUrls() {
        return getImageUrls();
    }

    // =========================================
    // SAMPLE IMAGES HELPER METHODS
    // =========================================

    /**
     * Get all sample images
     */
    public List<CameraSampleImage> getSampleImagesList() {
        if (sampleImages == null) {
            return new ArrayList<>();
        }
        return sampleImages.stream()
                .filter(img -> img.getImageUrl() != null && !img.getImageUrl().trim().isEmpty())
                .toList();
    }

    /**
     * Add a sample image
     */
    public void addSampleImage(String imageUrl, String title, Integer displayOrder) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return;

        CameraSampleImage sampleImage = CameraSampleImage.builder()
                .imageUrl(imageUrl)
                .title(title)
                .displayOrder(displayOrder != null ? displayOrder : sampleImages.size())
                .camera(this)
                .build();
        sampleImages.add(sampleImage);
    }

    /**
     * Add sample image from request
     */
    public void addSampleImageFromRequest(String imageUrl, String title) {
        addSampleImage(imageUrl, title, null);
    }

    /**
     * Clear all sample images
     */
    public void clearSampleImages() {
        sampleImages.clear();
    }

    /**
     * Set sample images from request list
     */
    public void setSampleImagesFromRequest(List<?> sampleImageRequests) {
        clearSampleImages();
        if (sampleImageRequests == null) return;

        for (int i = 0; i < sampleImageRequests.size(); i++) {
            Object req = sampleImageRequests.get(i);
            if (req instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) req;
                String url = (String) map.get("imageUrl");
                String title = (String) map.get("title");
                Integer order = map.get("displayOrder") != null ?
                        ((Number) map.get("displayOrder")).intValue() : i;
                addSampleImage(url, title, order);
            }
        }
    }

    /**
     * Add a sample image entity (used when loading from repository)
     * Does NOT set the camera reference - entity already has it from database
     */
    public void addSampleImageEntity(CameraSampleImage sampleImage) {
        if (sampleImage == null || sampleImage.getImageUrl() == null) return;
        // Avoid duplicates
        if (sampleImages.stream().noneMatch(img -> img.getId() != null && img.getId().equals(sampleImage.getId()))) {
            sampleImages.add(sampleImage);
        }
    }
}
