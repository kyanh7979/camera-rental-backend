package com.camerarental.controller;

import com.camerarental.dto.request.HomeBannerRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.HomeBannerResponse;
import com.camerarental.service.HomeBannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class HomeBannerController {

    private final HomeBannerService bannerService;

    // =========================================
    // PUBLIC ENDPOINTS
    // =========================================

    /**
     * Get all active banners for homepage (ordered by displayOrder)
     */
    @GetMapping("/banners/home")
    public ResponseEntity<ApiResponse<List<HomeBannerResponse>>> getHomeBanners() {
        log.info("[HomeBannerController] getHomeBanners called");
        List<HomeBannerResponse> banners = bannerService.getActiveBanners();
        return ResponseEntity.ok(ApiResponse.success(banners));
    }

    // =========================================
    // ADMIN ENDPOINTS
    // =========================================

    /**
     * Get all banners (including inactive) for admin
     */
    @GetMapping("/admin/banners")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<HomeBannerResponse>>> getAllBanners() {
        log.info("[HomeBannerController] getAllBanners called");
        return ResponseEntity.ok(ApiResponse.success(bannerService.getAllBanners()));
    }

    /**
     * Get banner by ID
     */
    @GetMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HomeBannerResponse>> getBannerById(@PathVariable Long id) {
        log.info("[HomeBannerController] getBannerById - id={}", id);
        return ResponseEntity.ok(ApiResponse.success(bannerService.getBannerById(id)));
    }

    /**
     * Create new banner
     */
    @PostMapping("/admin/banners")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HomeBannerResponse>> createBanner(
            @Valid @RequestBody HomeBannerRequest request) {
        log.info("[HomeBannerController] createBanner");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Banner created", bannerService.createBanner(request)));
    }

    /**
     * Create multiple banners at once (batch upload)
     */
    @PostMapping(value = "/admin/banners/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<HomeBannerResponse>>> createBannersBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "active", defaultValue = "true") Boolean active,
            @RequestParam(value = "startOrder", defaultValue = "0") Integer startOrder) {
        log.info("[HomeBannerController] createBannersBatch - count={}, active={}, startOrder={}", files.length, active, startOrder);
        List<HomeBannerResponse> created = bannerService.createBannersBatch(files, active, startOrder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm " + created.size() + " banner thành công", created));
    }

    /**
     * Update banner
     */
    @PutMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HomeBannerResponse>> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody HomeBannerRequest request) {
        log.info("[HomeBannerController] updateBanner - id={}", id);
        return ResponseEntity.ok(ApiResponse.success("Banner updated", bannerService.updateBanner(id, request)));
    }

    /**
     * Toggle banner active status
     */
    @PatchMapping("/admin/banners/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HomeBannerResponse>> toggleBanner(@PathVariable Long id) {
        log.info("[HomeBannerController] toggleBanner - id={}", id);
        HomeBannerResponse banner = bannerService.toggleBanner(id);
        String message = banner.getActive() ? "Banner enabled" : "Banner disabled";
        return ResponseEntity.ok(ApiResponse.success(message, banner));
    }

    /**
     * Delete banner
     */
    @DeleteMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        log.info("[HomeBannerController] deleteBanner - id={}", id);
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success("Banner deleted", null));
    }
}
