package com.camerarental.service.impl;

import com.camerarental.dto.response.S3UploadResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.CameraImage;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraImageRepository;
import com.camerarental.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/avif", "image/gif"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif"
    );

    private static final DateTimeFormatter PATH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final CameraRepository cameraRepository;
    private final CameraImageRepository cameraImageRepository;

    @Value("${app.storage.local.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.storage.local.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @Value("${app.storage.local.max-size-bytes:10485760}")
    private long maxSizeBytes;

    private Path uploadPath;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            log.info("[LocalStorage] Upload directory: {}", uploadPath);
        } catch (IOException e) {
            log.error("[LocalStorage] Failed to create upload directory: {}", e.getMessage());
        }
    }

    @Transactional
    public S3UploadResponse uploadImage(MultipartFile file, Long cameraId) {
        log.info("[LocalStorage] uploadImage - fileName: {}, size: {}, cameraId: {}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                cameraId);

        validateImage(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String filename = buildFilename(originalFilename);
        String relativePath = "images/" + LocalDateTime.now().format(PATH_TIME_FORMAT);

        try {
            Path targetDir = uploadPath.resolve(relativePath);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("[LocalStorage] File saved: {}", targetPath);
        } catch (IOException e) {
            log.error("[LocalStorage] Failed to save file: {}", e.getMessage());
            throw new BadRequestException("Không thể lưu file. Vui lòng thử lại.");
        }

        String url = buildPublicUrl(relativePath, filename);

        if (cameraId != null) {
            saveImageToDatabase(cameraId, url);
        }

        return S3UploadResponse.builder()
                .key(relativePath + "/" + filename)
                .url(url)
                .contentType(contentType)
                .size(file.getSize())
                .build();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File upload không được để trống");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Chỉ chấp nhận file ảnh: JPG, PNG, WEBP, AVIF, GIF");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename)) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BadRequestException("Đuôi file không hợp lệ. Chỉ chấp nhận: " + String.join(", ", ALLOWED_EXTENSIONS));
            }
        }

        long sizeBytes = file.getSize();
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new BadRequestException("Kích thước file vượt quá giới hạn. Tối đa: " + (maxSizeBytes / 1024 / 1024) + "MB");
        }
    }

    private String buildFilename(String originalFilename) {
        return UUID.randomUUID().toString() + getFileExtension(originalFilename);
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) return ".jpg";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return ".jpg";
        return filename.substring(dotIndex).toLowerCase();
    }

    private String buildPublicUrl(String relativePath, String filename) {
        String encodedPath = Arrays.stream(relativePath.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
        String encodedFile = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return baseUrl.replaceAll("/+$", "") + "/" + encodedPath + "/" + encodedFile;
    }

    private void saveImageToDatabase(Long cameraId, String imageUrl) {
        try {
            Camera camera = cameraRepository.findById(cameraId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", cameraId));

            long imageCount = cameraImageRepository.countByCameraId(cameraId);

            CameraImage cameraImage = CameraImage.builder()
                    .imageUrl(imageUrl)
                    .displayOrder((int) imageCount)
                    .camera(camera)
                    .build();

            cameraImageRepository.save(cameraImage);
            log.info("[LocalStorage] Image saved to DB - cameraId: {}", cameraId);
        } catch (Exception e) {
            log.error("[LocalStorage] Failed to save image to database: {}", e.getMessage());
        }
    }
}
