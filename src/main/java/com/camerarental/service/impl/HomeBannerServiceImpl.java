package com.camerarental.service.impl;

import com.camerarental.dto.request.HomeBannerRequest;
import com.camerarental.dto.response.HomeBannerResponse;
import com.camerarental.dto.response.S3UploadResponse;
import com.camerarental.entity.HomeBanner;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.HomeBannerRepository;
import com.camerarental.service.FileStorageService;
import com.camerarental.service.HomeBannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeBannerServiceImpl implements com.camerarental.service.HomeBannerService {

    private final HomeBannerRepository bannerRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<HomeBannerResponse> getActiveBanners() {
        log.info("[HomeBannerService] getActiveBanners called");
        List<HomeBanner> banners = bannerRepository.findAllActive();
        return banners.stream().map(HomeBannerResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeBannerResponse> getAllBanners() {
        log.info("[HomeBannerService] getAllBanners called");
        List<HomeBanner> banners = bannerRepository.findAllByOrderByDisplayOrderAsc();
        return banners.stream().map(HomeBannerResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HomeBannerResponse getBannerById(Long id) {
        log.info("[HomeBannerService] getBannerById - id={}", id);
        HomeBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", "id", id));
        return HomeBannerResponse.fromEntity(banner);
    }

    @Override
    @Transactional
    public HomeBannerResponse createBanner(HomeBannerRequest request) {
        log.info("[HomeBannerService] createBanner");

        HomeBanner banner = HomeBanner.builder()
                .imageUrl(request.getImageUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        HomeBanner saved = bannerRepository.save(banner);
        log.info("[HomeBannerService] Banner created - id={}", saved.getId());
        return HomeBannerResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public List<HomeBannerResponse> createBannersBatch(MultipartFile[] files, Boolean active, Integer startOrder) {
        log.info("[HomeBannerService] createBannersBatch - count={}, active={}, startOrder={}", 
                files.length, active, startOrder);
        
        List<HomeBannerResponse> created = new ArrayList<>();
        
        for (int i = 0; i < files.length; i++) {
            try {
                MultipartFile file = files[i];
                if (file == null || file.isEmpty()) {
                    log.warn("[HomeBannerService] Skipping empty file at index {}", i);
                    continue;
                }
                
                // Upload file
                S3UploadResponse uploadResult = fileStorageService.uploadImageSimple(file);
                String imageUrl = uploadResult.getUrl();
                
                // Create banner
                HomeBanner banner = HomeBanner.builder()
                        .imageUrl(imageUrl)
                        .active(active)
                        .displayOrder(startOrder + i)
                        .build();
                
                HomeBanner saved = bannerRepository.save(banner);
                created.add(HomeBannerResponse.fromEntity(saved));
                log.info("[HomeBannerService] Created banner id={} with order={}", saved.getId(), saved.getDisplayOrder());
                
            } catch (Exception e) {
                log.error("[HomeBannerService] Failed to create banner from file {}: {}", i, e.getMessage());
                // Continue with next file
            }
        }
        
        log.info("[HomeBannerService] Batch complete - created {} banners", created.size());
        return created;
    }

    @Override
    @Transactional
    public HomeBannerResponse updateBanner(Long id, HomeBannerRequest request) {
        log.info("[HomeBannerService] updateBanner - id={}", id);

        HomeBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", "id", id));

        if (request.getImageUrl() != null) banner.setImageUrl(request.getImageUrl());
        if (request.getActive() != null) banner.setActive(request.getActive());
        if (request.getDisplayOrder() != null) banner.setDisplayOrder(request.getDisplayOrder());

        HomeBanner saved = bannerRepository.save(banner);
        log.info("[HomeBannerService] Banner updated - id={}", saved.getId());
        return HomeBannerResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public HomeBannerResponse toggleBanner(Long id) {
        log.info("[HomeBannerService] toggleBanner - id={}", id);

        HomeBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", "id", id));

        banner.setActive(!banner.getActive());
        HomeBanner saved = bannerRepository.save(banner);

        log.info("[HomeBannerService] Banner toggled - id={}, active={}", saved.getId(), saved.getActive());
        return HomeBannerResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) {
        log.info("[HomeBannerService] deleteBanner - id={}", id);

        if (!bannerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Banner", "id", id);
        }

        bannerRepository.deleteById(id);
        log.info("[HomeBannerService] Banner deleted - id={}", id);
    }
}
