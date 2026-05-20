package com.camerarental.service;

import com.camerarental.dto.request.HomeBannerRequest;
import com.camerarental.dto.response.HomeBannerResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HomeBannerService {

    // Public - get active banners for homepage
    List<HomeBannerResponse> getActiveBanners();

    // Admin
    List<HomeBannerResponse> getAllBanners();
    HomeBannerResponse getBannerById(Long id);
    HomeBannerResponse createBanner(HomeBannerRequest request);
    List<HomeBannerResponse> createBannersBatch(MultipartFile[] files, Boolean active, Integer startOrder);
    HomeBannerResponse updateBanner(Long id, HomeBannerRequest request);
    HomeBannerResponse toggleBanner(Long id);
    void deleteBanner(Long id);
}
