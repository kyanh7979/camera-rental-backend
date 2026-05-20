package com.camerarental.service.impl;

import com.camerarental.dto.response.CameraResponse;
import com.camerarental.entity.Camera;
import com.camerarental.exception.BadRequestException;
import com.camerarental.repository.CameraRepository;
import com.camerarental.service.FileStorageService;
import com.camerarental.service.ProductService;
import com.camerarental.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final CameraRepository cameraRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public CameraResponse createProduct(String ten, String hang, BigDecimal gia, MultipartFile anh) {
        if (!StringUtils.hasText(ten)) {
            throw new BadRequestException("Tên sản phẩm không được để trống");
        }
        if (!StringUtils.hasText(hang)) {
            throw new BadRequestException("Hãng không được để trống");
        }
        if (gia == null || gia.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Giá phải lớn hơn 0");
        }

        Camera camera = Camera.builder()
                .name(ten)
                .slug(SlugUtil.toSlug(ten))
                .brand(hang)
                .dailyPrice(gia)
                .stock(1)
                .available(1)
                .isActive(true)
                .build();

        camera = cameraRepository.save(camera);

        if (anh != null && !anh.isEmpty()) {
            log.info("[ProductService] Uploading image for product: {}", camera.getId());
            fileStorageService.uploadImage(anh, camera.getId());
        }

        return CameraResponse.fromEntity(camera);
    }
}
