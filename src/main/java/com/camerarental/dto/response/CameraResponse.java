package com.camerarental.dto.response;

import com.camerarental.entity.Camera;
import com.camerarental.entity.CameraImage;
import com.camerarental.entity.CameraSampleImage;
import com.camerarental.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraResponse {

    private Long id;
    private String name;
    private String slug;
    private String brand;
    private String model;
    private String description;
    private BigDecimal dailyPrice;
    private BigDecimal deposit;
    private List<String> images;
    private List<SampleImageResponse> sampleImages;
    private Integer stock;
    private Integer available;
    private String specifications;
    private CategoryResponse category;
    private Boolean isActive;
    private LocalDateTime createdAt;

    /**
     * Convert entity to response WITHOUT sample images
     * Use for LIST/PAGED results where sample images are not needed
     */
    public static CameraResponse fromEntity(Camera camera) {
        if (camera == null) {
            return null;
        }

        // Safely extract image URLs
        List<String> imageUrls = new ArrayList<>();
        if (camera.getImages() != null && !camera.getImages().isEmpty()) {
            imageUrls = camera.getImages().stream()
                    .map(CameraImage::getImageUrl)
                    .filter(Objects::nonNull)
                    .filter(url -> !url.trim().isEmpty())
                    .toList();
        }

        // NO sample images for list response - avoids N+1 query
        CameraResponse response = CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .slug(camera.getSlug())
                .brand(camera.getBrand())
                .model(camera.getModel())
                .description(camera.getDescription())
                .dailyPrice(camera.getDailyPrice())
                .deposit(camera.getDeposit())
                .images(imageUrls)
                .sampleImages(null) // Explicitly null for list
                .stock(camera.getStock())
                .available(camera.getAvailable())
                .specifications(camera.getSpecifications())
                .isActive(camera.getIsActive())
                .createdAt(camera.getCreatedAt())
                .build();

        Category cat = camera.getCategory();
        if (cat != null) {
            response.setCategory(CategoryResponse.fromEntity(cat));
        }

        return response;
    }

    /**
     * Convert entity to response WITH sample images
     * Use for DETAIL/ADMIN results where sample images ARE needed
     * Camera entity must have sampleImages already loaded before calling this method
     */
    public static CameraResponse fromEntityWithSampleImages(Camera camera) {
        if (camera == null) {
            return null;
        }

        // Safely extract image URLs
        List<String> imageUrls = new ArrayList<>();
        if (camera.getImages() != null && !camera.getImages().isEmpty()) {
            imageUrls = camera.getImages().stream()
                    .map(CameraImage::getImageUrl)
                    .filter(Objects::nonNull)
                    .filter(url -> !url.trim().isEmpty())
                    .toList();
        }

        // Safely extract sample images (assumes already loaded)
        List<SampleImageResponse> sampleImageList = new ArrayList<>();
        if (camera.getSampleImages() != null && !camera.getSampleImages().isEmpty()) {
            sampleImageList = camera.getSampleImages().stream()
                    .filter(img -> img.getImageUrl() != null && !img.getImageUrl().trim().isEmpty())
                    .map(SampleImageResponse::fromEntity)
                    .toList();
        }

        CameraResponse response = CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .slug(camera.getSlug())
                .brand(camera.getBrand())
                .model(camera.getModel())
                .description(camera.getDescription())
                .dailyPrice(camera.getDailyPrice())
                .deposit(camera.getDeposit())
                .images(imageUrls)
                .sampleImages(sampleImageList)
                .stock(camera.getStock())
                .available(camera.getAvailable())
                .specifications(camera.getSpecifications())
                .isActive(camera.getIsActive())
                .createdAt(camera.getCreatedAt())
                .build();

        Category cat = camera.getCategory();
        if (cat != null) {
            response.setCategory(CategoryResponse.fromEntity(cat));
        }

        return response;
    }
}
