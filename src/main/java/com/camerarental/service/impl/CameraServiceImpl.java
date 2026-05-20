package com.camerarental.service.impl;

import com.camerarental.dto.request.CameraRequest;
import com.camerarental.dto.response.CameraResponse;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.CameraSampleImage;
import com.camerarental.entity.Category;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.DuplicateResourceException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraImageRepository;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.CameraSampleImageRepository;
import com.camerarental.repository.CategoryRepository;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.service.CameraService;
import com.camerarental.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CameraServiceImpl implements CameraService {

    private final CameraRepository cameraRepository;
    private final CategoryRepository categoryRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final CameraImageRepository cameraImageRepository;
    private final CameraSampleImageRepository cameraSampleImageRepository;

    /**
     * Helper method to load sample images for a camera entity
     * Avoids MultipleBagFetchException by loading separately from images
     */
    private void loadSampleImages(Camera camera) {
        if (camera == null || camera.getId() == null) return;
        List<CameraSampleImage> sampleImages = cameraSampleImageRepository
                .findByCameraIdOrderByDisplayOrderAsc(camera.getId());
        sampleImages.forEach(camera::addSampleImageEntity);
    }

    /**
     * Helper method to load sample images for multiple cameras (avoids N+1)
     */
    private void loadSampleImagesForCameras(List<Camera> cameras) {
        if (cameras == null || cameras.isEmpty()) return;
        List<Long> cameraIds = cameras.stream().map(Camera::getId).toList();
        List<CameraSampleImage> allSampleImages = cameraSampleImageRepository
                .findByCameraIdIn(cameraIds);
        // Group by camera ID
        java.util.Map<Long, List<CameraSampleImage>> samplesByCamera = allSampleImages.stream()
                .collect(java.util.stream.Collectors.groupingBy(img -> img.getCamera().getId()));
        // Add to each camera
        cameras.forEach(camera -> {
            List<CameraSampleImage> samples = samplesByCamera.getOrDefault(camera.getId(), List.of());
            samples.forEach(camera::addSampleImageEntity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CameraResponse> searchCameras(String keyword, String brand, Long categoryId,
            BigDecimal minPrice, BigDecimal maxPrice,
            Boolean available, int page, int size,
            String sortBy, String sortDir) {
        
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 50);
        
        List<String> allowedSortFields = List.of("id", "name", "brand", "model", "dailyPrice", "stock", "available", "createdAt");
        String safeSortField = allowedSortFields.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), safeSortField);
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        log.info("[CameraService] searchCameras - page={}, size={}, keyword={}, brand={}, categoryId={}",
                safePage, safeSize, keyword, brand, categoryId);

        Page<Camera> cameras = cameraRepository.searchCameras(keyword, brand, categoryId,
                minPrice, maxPrice, available, pageable);

        log.info("[CameraService] searchCameras - found {} cameras (total: {})",
                cameras.getNumberOfElements(), cameras.getTotalElements());

        return PagedResponse.<CameraResponse>builder()
                .content(cameras.getContent().stream().map(CameraResponse::fromEntity).toList())
                .page(cameras.getNumber())
                .size(cameras.getSize())
                .totalElements(cameras.getTotalElements())
                .totalPages(cameras.getTotalPages())
                .last(cameras.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CameraResponse> getSliderCameras() {
        log.info("[CameraService] getSliderCameras called");

        // Use N+1 safe query with FETCH JOIN
        List<Camera> cameras = cameraRepository.findAllActiveCamerasWithImages();

        log.info("[CameraService] getSliderCameras - found {} cameras", cameras.size());

        // Log image counts
        cameras.forEach(c -> {
            int imgCount = c.getImages() != null ? c.getImages().size() : 0;
            log.info("[CameraService] Camera {} '{}' has {} images", c.getId(), c.getName(), imgCount);
        });

        // NOTE: sampleImages NOT loaded here - only for detail page
        return cameras.stream()
                .map(CameraResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CameraResponse getCameraBySlug(String slug) {
        log.info("[CameraService] getCameraBySlug - slug={}", slug);
        Camera camera = cameraRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "slug", slug));
        return CameraResponse.fromEntity(camera);
    }

    @Override
    @Transactional(readOnly = true)
    public CameraResponse getCameraById(Long id) {
        log.info("[CameraService] getCameraById - id={}", id);
        Camera camera = cameraRepository.findByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));
        // Load sample images separately to avoid MultipleBagFetchException
        loadSampleImages(camera);
        // Use mapper that includes sampleImages
        return CameraResponse.fromEntityWithSampleImages(camera);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctBrands() {
        List<String> brands = cameraRepository.findDistinctBrands();
        log.info("[CameraService] getDistinctBrands - found {} brands", brands.size());
        return brands;
    }

    @Override
    @Transactional
    public CameraResponse createCamera(CameraRequest request) {
        // Validate name
        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        if (!StringUtils.hasText(trimmedName)) {
            throw new BadRequestException("Tên sản phẩm không được để trống");
        }
        if (trimmedName.length() > 255) {
            throw new BadRequestException("Tên sản phẩm không được quá 255 ký tự");
        }
        if (cameraRepository.existsByNameAndDeletedFalse(trimmedName)) {
            throw new DuplicateResourceException("Camera", "name", trimmedName);
        }

        // Validate price
        if (request.getDailyPrice() == null || request.getDailyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Giá thuê theo ngày phải lớn hơn 0");
        }

        // Build camera
        Camera.CameraBuilder builder = Camera.builder()
                .name(trimmedName)
                .slug(SlugUtil.toSlug(trimmedName))
                .brand(request.getBrand())
                .model(request.getModel())
                .description(request.getDescription())
                .dailyPrice(request.getDailyPrice())
                .deposit(request.getDeposit())
                .stock(request.getStock() != null ? request.getStock() : 1)
                .available(request.getStock() != null ? request.getStock() : 1)
                .specifications(request.getSpecifications())
                .isActive(true)
                .deleted(false)
                .rating(BigDecimal.valueOf(5.0));

        // Set images if provided
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            builder.images(new java.util.ArrayList<>());
        }

        // Set category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            builder.category(category);
        } else if (request.getCategory() != null && !request.getCategory().trim().isEmpty()) {
            Category category = categoryRepository.findByName(request.getCategory().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "name", request.getCategory()));
            builder.category(category);
        }

        Camera saved = cameraRepository.save(builder.build());

        // Add images after save (to get the camera ID)
        if (request.getImages() != null) {
            request.getImages().forEach(saved::addImage);
            saved = cameraRepository.save(saved);
        }

        // Add sample images after save (to get the camera ID)
        if (request.getSampleImages() != null && !request.getSampleImages().isEmpty()) {
            for (int i = 0; i < request.getSampleImages().size(); i++) {
                var sampleImg = request.getSampleImages().get(i);
                if (sampleImg.getImageUrl() != null && !sampleImg.getImageUrl().trim().isEmpty()) {
                    saved.addSampleImage(
                            sampleImg.getImageUrl(),
                            sampleImg.getTitle(),
                            sampleImg.getDisplayOrder() != null ? sampleImg.getDisplayOrder() : i
                    );
                }
            }
            saved = cameraRepository.save(saved);
        }

        log.info("[CameraService] Created camera: id={}, name={}", saved.getId(), saved.getName());
        return CameraResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CameraResponse updateCamera(Long id, CameraRequest request) {
        Camera camera = cameraRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        // Validate name
        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        if (trimmedName != null && !trimmedName.isEmpty() && !trimmedName.equalsIgnoreCase(camera.getName())) {
            if (trimmedName.length() > 255) {
                throw new BadRequestException("Tên sản phẩm không được quá 255 ký tự");
            }
            if (cameraRepository.existsByNameAndDeletedFalse(trimmedName)) {
                throw new DuplicateResourceException("Camera", "name", trimmedName);
            }
            camera.setName(trimmedName);
            camera.setSlug(SlugUtil.toSlug(trimmedName));
        }

        // Update fields
        if (request.getBrand() != null) camera.setBrand(request.getBrand());
        if (request.getModel() != null) camera.setModel(request.getModel());
        if (request.getDescription() != null) camera.setDescription(request.getDescription());
        
        if (request.getDailyPrice() != null) {
            if (request.getDailyPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Giá thuê theo ngày phải lớn hơn 0");
            }
            camera.setDailyPrice(request.getDailyPrice());
        }
        
        if (request.getDeposit() != null) camera.setDeposit(request.getDeposit());
        if (request.getImages() != null) {
            // Update images - clear old and add new
            camera.clearImages();
            request.getImages().forEach(camera::addImage);
        }
        // Update sample images only if provided (preserve existing if not provided)
        if (request.getSampleImages() != null) {
            camera.clearSampleImages();
            for (int i = 0; i < request.getSampleImages().size(); i++) {
                var sampleImg = request.getSampleImages().get(i);
                if (sampleImg.getImageUrl() != null && !sampleImg.getImageUrl().trim().isEmpty()) {
                    camera.addSampleImage(
                            sampleImg.getImageUrl(),
                            sampleImg.getTitle(),
                            sampleImg.getDisplayOrder() != null ? sampleImg.getDisplayOrder() : i
                    );
                }
            }
        }
        if (request.getSpecifications() != null) camera.setSpecifications(request.getSpecifications());
        if (request.getStock() != null) {
            camera.setStock(request.getStock());
            camera.setAvailable(request.getStock());
        }

        // Update category
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            camera.setCategory(category);
        } else if (request.getCategory() != null && !request.getCategory().trim().isEmpty()) {
            Category category = categoryRepository.findByName(request.getCategory().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "name", request.getCategory()));
            camera.setCategory(category);
        } else if (request.getCategory() != null && request.getCategory().trim().isEmpty()) {
            camera.setCategory(null);
        }

        Camera saved = cameraRepository.save(camera);
        log.info("[CameraService] Updated camera: id={}, name={}", saved.getId(), saved.getName());
        return CameraResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CameraResponse updateCameraCategory(Long id, Long categoryId) {
        Camera camera = cameraRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
            camera.setCategory(category);
        } else {
            camera.setCategory(null);
        }

        return CameraResponse.fromEntity(cameraRepository.save(camera));
    }

    @Override
    @Transactional
    public CameraResponse hideCamera(Long id) {
        log.info("[CameraService] hideCamera called - id={}", id);

        Camera camera = cameraRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        if (Boolean.TRUE.equals(camera.getDeleted())) {
            throw new BadRequestException("Sản phẩm đã được xóa trước đó");
        }

        camera.setIsActive(false);
        Camera saved = cameraRepository.save(camera);

        log.info("[CameraService] Camera hidden: id={}, name={}", id, saved.getName());
        return CameraResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CameraResponse showCamera(Long id) {
        log.info("[CameraService] showCamera called - id={}", id);

        Camera camera = cameraRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        if (Boolean.TRUE.equals(camera.getDeleted())) {
            throw new BadRequestException("Sản phẩm đã được xóa trước đó");
        }

        camera.setIsActive(true);
        Camera saved = cameraRepository.save(camera);

        log.info("[CameraService] Camera shown: id={}, name={}", id, saved.getName());
        return CameraResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CameraResponse> getAllCamerasForAdmin() {
        log.info("[CameraService] getAllCamerasForAdmin called");
        List<Camera> cameras = cameraRepository.findAllByDeletedFalse();
        log.info("[CameraService] getAllCamerasForAdmin - found {} cameras", cameras.size());
        // NOTE: sampleImages NOT loaded for list - only loaded when editing individual camera via getCameraById
        return cameras.stream().map(CameraResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public void deleteCamera(Long id) {
        log.info("[CameraService] deleteCamera called - id={}", id);

        Camera camera = cameraRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        // Check if already deleted
        if (Boolean.TRUE.equals(camera.getDeleted())) {
            log.warn("[CameraService] Camera already deleted: id={}", id);
            throw new BadRequestException("Sản phẩm đã được xóa trước đó");
        }

        // Check if camera has active rental orders - block only this
        long activeOrderCount = rentalOrderRepository.countActiveOrdersByCameraId(
                id,
                java.util.List.of(
                        OrderStatus.PENDING,
                        OrderStatus.CONFIRMED,
                        OrderStatus.RENTING
                )
        );
        if (activeOrderCount > 0) {
            log.warn("[CameraService] Cannot delete camera with active orders: id={}, orderCount={}", id, activeOrderCount);
            throw new BadRequestException(
                    "Không thể xóa sản phẩm đang có " + activeOrderCount + " đơn thuê đang hoạt động. Vui lòng hoàn thành hoặc hủy đơn trước.");
        }

        // SOFT DELETE - always allowed if no active orders
        camera.setDeleted(true);
        camera.setIsActive(false);
        cameraRepository.save(camera);

        log.info("[CameraService] Soft deleted camera: id={}, name={}", id, camera.getName());
    }
}
