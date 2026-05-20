package com.camerarental.controller;

import com.camerarental.dto.request.CameraRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.CameraResponse;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.exception.BadRequestException;
import com.camerarental.service.CameraService;
import com.camerarental.util.AppConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
@Slf4j
public class CameraController {

    private final CameraService cameraService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CameraResponse>>> searchCameras(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIR) String sortDir) {
        log.info("[CameraController] searchCameras - page={}, size={}", page, size);
        return ResponseEntity.ok(ApiResponse.success(
                cameraService.searchCameras(keyword, brand, categoryId, minPrice, maxPrice,
                        available, page, size, sortBy, sortDir)));
    }

    /**
     * Get all cameras for hero slider
     * Returns list of all active cameras with images - no pagination
     */
    @GetMapping("/slider")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getSliderCameras() {
        log.info("[CameraController] getSliderCameras called");
        List<CameraResponse> cameras = cameraService.getSliderCameras();
        log.info("[CameraController] getSliderCameras - returning {} cameras", cameras.size());
        return ResponseEntity.ok(ApiResponse.success(cameras));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CameraResponse>> getCameraBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getCameraBySlug(slug)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CameraResponse>> getCameraById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getCameraById(id)));
    }

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<String>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getDistinctBrands()));
    }

    /**
     * Get all cameras for admin (including inactive/deleted=false)
     * This endpoint returns ALL non-deleted cameras for admin management
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getAllCamerasForAdmin() {
        log.info("[CameraController] getAllCamerasForAdmin called");
        return ResponseEntity.ok(ApiResponse.success(cameraService.getAllCamerasForAdmin()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> createCamera(@Valid @RequestBody CameraRequest request) {
        log.info("[CameraController] createCamera - name={}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Camera created", cameraService.createCamera(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> updateCamera(
            @PathVariable Long id,
            @Valid @RequestBody CameraRequest request) {
        log.info("[CameraController] updateCamera - id={}", id);
        return ResponseEntity.ok(ApiResponse.success("Camera updated", cameraService.updateCamera(id, request)));
    }

    @PatchMapping("/{id}/category")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> updateCameraCategory(
            @PathVariable Long id,
            @RequestBody CameraRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated",
                cameraService.updateCameraCategory(id, request.getCategoryId())));
    }

    @PatchMapping("/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> hideCamera(@PathVariable Long id) {
        log.info("[CameraController] hideCamera - id={}", id);
        return ResponseEntity.ok(ApiResponse.success("Camera hidden", cameraService.hideCamera(id)));
    }

    @PatchMapping("/{id}/show")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> showCamera(@PathVariable Long id) {
        log.info("[CameraController] showCamera - id={}", id);
        return ResponseEntity.ok(ApiResponse.success("Camera shown", cameraService.showCamera(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCamera(@PathVariable Long id) {
        log.info("[CameraController] deleteCamera - id={}", id);
        try {
            cameraService.deleteCamera(id);
            return ResponseEntity.ok(ApiResponse.success("Camera deleted", null));
        } catch (BadRequestException e) {
            // Return 409 CONFLICT for business rule violations
            log.warn("[CameraController] Delete conflict - id={}, reason={}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
