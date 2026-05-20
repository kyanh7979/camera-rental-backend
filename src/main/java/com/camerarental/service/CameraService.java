package com.camerarental.service;

import com.camerarental.dto.request.CameraRequest;
import com.camerarental.dto.response.CameraResponse;
import com.camerarental.dto.response.PagedResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CameraService {

    PagedResponse<CameraResponse> searchCameras(String keyword, String brand, Long categoryId,
                                                  BigDecimal minPrice, BigDecimal maxPrice,
                                                  Boolean available, int page, int size,
                                                  String sortBy, String sortDir);

    // Get all cameras for slider - returns list (not paged)
    List<CameraResponse> getSliderCameras();

    CameraResponse getCameraBySlug(String slug);

    CameraResponse getCameraById(Long id);

    List<String> getDistinctBrands();

    CameraResponse createCamera(CameraRequest request);

    CameraResponse updateCamera(Long id, CameraRequest request);

    // Update chỉ category
    CameraResponse updateCameraCategory(Long id, Long categoryId);

    // Hide (deactivate) camera
    CameraResponse hideCamera(Long id);

    // Show (activate) camera
    CameraResponse showCamera(Long id);

    // Get all cameras for admin (including inactive) - returns list (not paged)
    List<CameraResponse> getAllCamerasForAdmin();

    void deleteCamera(Long id);
}
